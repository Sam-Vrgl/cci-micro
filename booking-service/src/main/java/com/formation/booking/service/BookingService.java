package com.formation.booking.service;

import com.formation.booking.client.ClassClient;
import com.formation.booking.client.PaymentClient;
import com.formation.booking.dto.BookingRequest;
import com.formation.booking.dto.BookingResponse;
import com.formation.booking.dto.ConfirmPaymentRequest;
import com.formation.booking.dto.FitnessClassDto;
import com.formation.booking.dto.PaymentRequestDto;
import com.formation.booking.dto.PaymentResponseDto;
import com.formation.booking.exception.BookingNotFoundException;
import com.formation.booking.exception.CancellationDeadlineExceededException;
import com.formation.booking.exception.ClassNotBookableException;
import com.formation.booking.exception.InvalidBookingStateException;
import com.formation.booking.exception.NoSpotsAvailableException;
import com.formation.booking.exception.PaymentDeadlineExceededException;
import com.formation.booking.exception.PaymentRefusedException;
import com.formation.booking.model.Booking;
import com.formation.booking.model.BookingStatus;
import com.formation.booking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrateur de la saga de reservation. Les appels distants sont volontairement hors
 * transaction : une transaction ouverte pendant un appel reseau bloquerait une connexion, et la
 * coherence est retablie par compensation explicite, pas par un rollback.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private static final String REFERENCE_PREFIX = "BK-";
    private static final String REFERENCE_ALPHABET = "ABCDEFGHIJKLMNPQRSTUVWXYZ123456789";
    private static final int REFERENCE_LENGTH = 5;
    private static final int MAX_REFERENCE_ATTEMPTS = 10;

    private final BookingRepository bookingRepository;
    private final ClassClient classClient;
    private final PaymentClient paymentClient;
    private final NotificationPublisher notificationPublisher;
    private final SecureRandom random = new SecureRandom();

    public BookingService(BookingRepository bookingRepository, ClassClient classClient,
                          PaymentClient paymentClient, NotificationPublisher notificationPublisher) {
        this.bookingRepository = bookingRepository;
        this.classClient = classClient;
        this.paymentClient = paymentClient;
        this.notificationPublisher = notificationPublisher;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findAll() {
        return bookingRepository.findAll().stream()
                .map(BookingMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse findById(Long id) {
        return BookingMapper.toResponse(getBookingOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findByUserId(Long userId) {
        return bookingRepository.findByUserIdOrderByBookingDateDesc(userId).stream()
                .map(BookingMapper::toResponse)
                .toList();
    }

    /** Reservations impayees dont le delai est ecoule ; consommee par le scheduler. */
    @Transactional(readOnly = true)
    public List<BookingResponse> findExpired() {
        return findExpiredEntities().stream()
                .map(BookingMapper::toResponse)
                .toList();
    }

    /**
     * Cas 1 de la saga : verification du cours, reservation des places, persistance, notification.
     * Si la persistance echoue apres la prise des places, celles-ci sont rendues (compensation).
     */
    public BookingResponse create(BookingRequest request) {
        int spots = request.getNumberOfSpots();

        // Etape 1 : verification du cours et capture de l'instantane
        FitnessClassDto fitnessClass = classClient.getById(request.getClassId());
        if (!fitnessClass.isScheduled()) {
            throw new ClassNotBookableException(request.getClassId(), fitnessClass.getStatus());
        }
        if (!fitnessClass.hasAvailableSpots(spots)) {
            throw new NoSpotsAvailableException(request.getClassId());
        }

        // Etape 2 : reservation effective des places (409 du class-service si conflit)
        classClient.increment(request.getClassId(), spots);

        // Etape 3 : persistance de la reservation
        Booking booking;
        try {
            booking = bookingRepository.save(buildBooking(request, fitnessClass, spots));
        } catch (RuntimeException ex) {
            compensateSpots(request.getClassId(), spots, "echec de la creation de la reservation");
            throw ex;
        }

        log.info("Reservation {} creee pour l'utilisateur {} ({} place(s), {} EUR)",
                booking.getBookingReference(), booking.getUserId(), spots, booking.getTotalAmount());

        // Etape 4 : notification, sans effet sur l'issue de la saga
        notificationPublisher.bookingConfirmation(booking);

        return BookingMapper.toResponse(booking);
    }

    /**
     * Cas 3 de la saga : paiement puis confirmation. Un paiement refuse laisse la reservation en
     * PENDING_PAYMENT : l'utilisateur peut retenter jusqu'a l'echeance, apres quoi le scheduler
     * annulera la reservation et liberera les places.
     */
    public BookingResponse confirm(Long id, ConfirmPaymentRequest request) {
        Booking booking = getBookingOrThrow(id);

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidBookingStateException(id, booking.getStatus(), "PENDING_PAYMENT");
        }
        if (booking.isPaymentWindowClosed(LocalDateTime.now())) {
            throw new PaymentDeadlineExceededException(id, booking.getPaymentDeadline());
        }

        PaymentResponseDto payment = paymentClient.process(new PaymentRequestDto(booking.getId(),
                booking.getBookingReference(), booking.getUserId(), booking.getTotalAmount(),
                request.getPaymentMethod(), request.getCardLastFour(), request.getTransactionId()));

        if (!payment.isSuccessful()) {
            log.info("Paiement refuse pour la reservation {} : elle reste en attente de paiement",
                    booking.getBookingReference());
            throw new PaymentRefusedException(id, payment.getPaymentReference(),
                    payment.getFailureReason());
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        Booking confirmed = bookingRepository.save(booking);

        log.info("Reservation {} confirmee par le paiement {}", confirmed.getBookingReference(),
                payment.getPaymentReference());

        notificationPublisher.paymentConfirmation(confirmed, payment.getPaymentReference());

        return BookingMapper.toResponse(confirmed);
    }

    /**
     * Cas 4 de la saga : annulation dans les delais, remboursement eventuel et liberation des places.
     */
    public BookingResponse cancel(Long id) {
        Booking booking = getBookingOrThrow(id);

        if (booking.isClosed()) {
            throw new InvalidBookingStateException(id, booking.getStatus(),
                    "PENDING_PAYMENT ou CONFIRMED");
        }
        if (booking.isCancellationWindowClosed(LocalDateTime.now())) {
            throw new CancellationDeadlineExceededException(id, booking.getCancellationDeadline());
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            refundIfPossible(booking);
        }

        // Les places sont prises des la creation : elles sont liberees quel que soit le statut actif.
        // Le cahier des charges place cette etape sous "si CONFIRMED", mais s'y tenir laisserait
        // des places bloquees pour toute reservation annulee avant paiement.
        classClient.decrement(booking.getClassId(), booking.getNumberOfSpots());

        Booking cancelled = markCancelled(booking);

        notificationPublisher.bookingCancelled(cancelled, "annulation par l'utilisateur");

        return BookingMapper.toResponse(cancelled);
    }

    public BookingResponse complete(Long id) {
        Booking booking = getBookingOrThrow(id);

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException(id, booking.getStatus(), "CONFIRMED");
        }

        booking.setStatus(BookingStatus.COMPLETED);
        return BookingMapper.toResponse(bookingRepository.save(booking));
    }

    /**
     * Annule les reservations dont le delai de paiement est ecoule et libere leurs places.
     * Chaque reservation est traitee isolement : un service indisponible ne doit pas interrompre
     * le lot.
     *
     * @return le nombre de reservations effectivement annulees
     */
    public int cancelExpiredBookings() {
        List<Booking> expired = findExpiredEntities();
        int cancelledCount = 0;

        for (Booking booking : expired) {
            try {
                classClient.decrement(booking.getClassId(), booking.getNumberOfSpots());
                Booking cancelled = markCancelled(booking);
                notificationPublisher.bookingCancelled(cancelled, "delai de paiement depasse");
                cancelledCount++;
            } catch (RuntimeException ex) {
                log.error("Expiration de la reservation {} impossible, elle sera retentee au "
                        + "prochain passage : {}", booking.getBookingReference(), ex.toString());
            }
        }

        if (cancelledCount > 0) {
            log.info("{} reservation(s) expiree(s) annulee(s) sur {} candidate(s)", cancelledCount,
                    expired.size());
        }
        return cancelledCount;
    }

    /**
     * Envoie un rappel pour les cours confirmes qui commencent dans moins de 24 heures.
     *
     * @return le nombre de rappels envoyes
     */
    public int sendClassReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> upcoming = bookingRepository.findByStatusAndReminderSentFalseAndClassDateBetween(
                BookingStatus.CONFIRMED, now, now.plusHours(Booking.CANCELLATION_WINDOW_HOURS));

        for (Booking booking : upcoming) {
            notificationPublisher.bookingReminder(booking);
            booking.setReminderSent(true);
            bookingRepository.save(booking);
        }

        if (!upcoming.isEmpty()) {
            log.info("{} rappel(s) de cours envoye(s)", upcoming.size());
        }
        return upcoming.size();
    }

    private void refundIfPossible(Booking booking) {
        PaymentResponseDto payment = paymentClient.getByBookingId(booking.getId());

        if (payment == null || !payment.isSuccessful()) {
            log.warn("Aucun paiement encaisse a rembourser pour la reservation {}",
                    booking.getBookingReference());
            return;
        }

        PaymentResponseDto refund = paymentClient.refund(payment.getId());
        if (refund != null) {
            log.info("Paiement {} rembourse pour la reservation {}", payment.getPaymentReference(),
                    booking.getBookingReference());
        }
    }

    /** Appel interne : le save du repository porte sa propre transaction. */
    private Booking markCancelled(Booking booking) {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationDate(LocalDateTime.now());
        return bookingRepository.save(booking);
    }

    private void compensateSpots(Long classId, int spots, String reason) {
        try {
            classClient.decrement(classId, spots);
            log.warn("Compensation : {} place(s) rendue(s) au cours {} ({})", spots, classId, reason);
        } catch (RuntimeException ex) {
            log.error("Compensation impossible : {} place(s) restent prises sur le cours {} ({})",
                    spots, classId, ex.toString());
        }
    }

    private List<Booking> findExpiredEntities() {
        return bookingRepository.findByStatusAndPaymentDeadlineBefore(BookingStatus.PENDING_PAYMENT,
                LocalDateTime.now());
    }

    private Booking buildBooking(BookingRequest request, FitnessClassDto fitnessClass, int spots) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal totalAmount = fitnessClass.getPrice().multiply(BigDecimal.valueOf(spots));

        return new Booking(generateReference(), request.getUserId(), request.getUserEmail(),
                request.getUserName(), fitnessClass.getId(), fitnessClass.getName(),
                fitnessClass.getDateTime(), fitnessClass.getInstructor(), fitnessClass.getPrice(),
                spots, totalAmount, now, BookingStatus.PENDING_PAYMENT,
                now.plusHours(Booking.PAYMENT_WINDOW_HOURS),
                fitnessClass.getDateTime().minusHours(Booking.CANCELLATION_WINDOW_HOURS));
    }

    private Booking getBookingOrThrow(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));
    }

    private String generateReference() {
        for (int attempt = 0; attempt < MAX_REFERENCE_ATTEMPTS; attempt++) {
            StringBuilder reference = new StringBuilder(REFERENCE_PREFIX);
            for (int i = 0; i < REFERENCE_LENGTH; i++) {
                reference.append(REFERENCE_ALPHABET.charAt(random.nextInt(REFERENCE_ALPHABET.length())));
            }
            String candidate = reference.toString();
            if (!bookingRepository.existsByBookingReference(candidate)) {
                return candidate;
            }
        }
        return REFERENCE_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

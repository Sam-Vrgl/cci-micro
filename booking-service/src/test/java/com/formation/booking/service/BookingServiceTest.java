package com.formation.booking.service;

import com.formation.booking.client.ClassClient;
import com.formation.booking.client.PaymentClient;
import com.formation.booking.dto.BookingRequest;
import com.formation.booking.dto.BookingResponse;
import com.formation.booking.dto.ConfirmPaymentRequest;
import com.formation.booking.dto.FitnessClassDto;
import com.formation.booking.dto.PaymentRequestDto;
import com.formation.booking.dto.PaymentResponseDto;
import com.formation.booking.exception.CancellationDeadlineExceededException;
import com.formation.booking.exception.ClassNotBookableException;
import com.formation.booking.exception.InvalidBookingStateException;
import com.formation.booking.exception.NoSpotsAvailableException;
import com.formation.booking.exception.PaymentDeadlineExceededException;
import com.formation.booking.exception.PaymentRefusedException;
import com.formation.booking.model.Booking;
import com.formation.booking.model.BookingStatus;
import com.formation.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ClassClient classClient;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private BookingService bookingService;

    private FitnessClassDto fitnessClass(int maxParticipants, int currentParticipants) {
        FitnessClassDto dto = new FitnessClassDto();
        dto.setId(101L);
        dto.setName("Yoga Vinyasa");
        dto.setInstructor("Marie Dupont");
        dto.setPrice(new BigDecimal("15.00"));
        dto.setDateTime(LocalDateTime.now().plusDays(5));
        dto.setMaxParticipants(maxParticipants);
        dto.setCurrentParticipants(currentParticipants);
        dto.setAvailableSpots(maxParticipants - currentParticipants);
        dto.setStatus("SCHEDULED");
        return dto;
    }

    private BookingRequest request(int spots) {
        return new BookingRequest(1L, "john@example.com", "John Doe", 101L, spots);
    }

    private Booking booking(Long id, BookingStatus status, LocalDateTime classDate) {
        Booking booking = new Booking("BK-AB123", 1L, "john@example.com", "John Doe", 101L,
                "Yoga Vinyasa", classDate, "Marie Dupont", new BigDecimal("15.00"), 2,
                new BigDecimal("30.00"), LocalDateTime.now().minusMinutes(10), status,
                LocalDateTime.now().plusMinutes(50),
                classDate.minusHours(Booking.CANCELLATION_WINDOW_HOURS));
        booking.setId(id);
        return booking;
    }

    private PaymentResponseDto payment(Long id, String status) {
        PaymentResponseDto payment = new PaymentResponseDto();
        payment.setId(id);
        payment.setPaymentReference("PAY-XY12Z");
        payment.setBookingId(10L);
        payment.setAmount(new BigDecimal("30.00"));
        payment.setStatus(status);
        if (!"SUCCESS".equals(status)) {
            payment.setFailureReason("Montant superieur ou egal a 100.00 EUR");
        }
        return payment;
    }

    private void givenSaveReturnsEntity() {
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(10L);
            }
            return saved;
        });
    }

    // --- Cas 1 : creation de la reservation ---

    @Test
    void shouldCreateBooking_whenSpotsAvailable() {
        // Given : un cours de 10 places dont 5 sont prises
        when(classClient.getById(101L)).thenReturn(fitnessClass(10, 5));
        givenSaveReturnsEntity();

        // When : reservation de 2 places
        BookingResponse result = bookingService.create(request(2));

        // Then : la reservation est en attente de paiement et les places sont prises
        assertThat(result.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(result.getBookingReference()).startsWith("BK-");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("30.00");
        assertThat(result.getPaymentDeadline()).isAfter(LocalDateTime.now());
        assertThat(result.getCancellationDeadline())
                .isEqualTo(result.getClassDate().minusHours(24));
        verify(classClient).increment(101L, 2);
        verify(notificationPublisher).bookingConfirmation(any(Booking.class));
    }

    @Test
    void shouldThrowException_whenNoSpotsAvailable() {
        // Given : un cours de 10 places dont 9 sont prises
        when(classClient.getById(101L)).thenReturn(fitnessClass(10, 9));

        // When / Then : reserver 2 places est refuse avant meme d'appeler class-service
        assertThatThrownBy(() -> bookingService.create(request(2)))
                .isInstanceOf(NoSpotsAvailableException.class);

        verify(classClient, never()).increment(anyLong(), anyInt());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void create_coursAnnule_leveClassNotBookableException() {
        FitnessClassDto cancelled = fitnessClass(10, 0);
        cancelled.setStatus("CANCELLED");
        when(classClient.getById(101L)).thenReturn(cancelled);

        assertThatThrownBy(() -> bookingService.create(request(1)))
                .isInstanceOf(ClassNotBookableException.class);

        verify(classClient, never()).increment(anyLong(), anyInt());
    }

    @Test
    void create_echecDePersistance_compenseEnLiberantLesPlaces() {
        when(classClient.getById(101L)).thenReturn(fitnessClass(10, 5));
        when(bookingRepository.save(any(Booking.class)))
                .thenThrow(new IllegalStateException("base indisponible"));

        assertThatThrownBy(() -> bookingService.create(request(2)))
                .isInstanceOf(IllegalStateException.class);

        verify(classClient).increment(101L, 2);
        verify(classClient).decrement(101L, 2);
    }

    @Test
    void create_calculeLeMontantTotalDepuisLePrixDuCours() {
        when(classClient.getById(101L)).thenReturn(fitnessClass(10, 0));
        givenSaveReturnsEntity();

        assertThat(bookingService.create(request(4)).getTotalAmount()).isEqualByComparingTo("60.00");
    }

    // --- Cas 3 : confirmation apres paiement ---

    @Test
    void confirm_paiementAccepte_passeLaReservationEnConfirmed() {
        Booking pending = booking(10L, BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusDays(5));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(pending));
        when(paymentClient.process(any(PaymentRequestDto.class))).thenReturn(payment(7L, "SUCCESS"));
        givenSaveReturnsEntity();

        BookingResponse result = bookingService.confirm(10L,
                new ConfirmPaymentRequest("CREDIT_CARD", "1234", "txn_123456"));

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(notificationPublisher).paymentConfirmation(any(Booking.class), eq("PAY-XY12Z"));
    }

    @Test
    void confirm_transmetLeMontantEtLaReferenceAuPaymentService() {
        Booking pending = booking(10L, BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusDays(5));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(pending));
        when(paymentClient.process(any(PaymentRequestDto.class))).thenReturn(payment(7L, "SUCCESS"));
        givenSaveReturnsEntity();

        bookingService.confirm(10L, new ConfirmPaymentRequest("CREDIT_CARD", "1234", null));

        ArgumentCaptor<PaymentRequestDto> captor = ArgumentCaptor.forClass(PaymentRequestDto.class);
        verify(paymentClient).process(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("30.00");
        assertThat(captor.getValue().getBookingReference()).isEqualTo("BK-AB123");
        assertThat(captor.getValue().getPaymentMethod()).isEqualTo("CREDIT_CARD");
    }

    @Test
    void confirm_paiementRefuse_laisseLaReservationEnAttenteDePaiement() {
        Booking pending = booking(10L, BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusDays(5));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(pending));
        when(paymentClient.process(any(PaymentRequestDto.class))).thenReturn(payment(7L, "FAILED"));

        assertThatThrownBy(() -> bookingService.confirm(10L,
                new ConfirmPaymentRequest("CREDIT_CARD", "1234", null)))
                .isInstanceOf(PaymentRefusedException.class);

        assertThat(pending.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void confirm_delaiDePaiementDepasse_levePaymentDeadlineExceededException() {
        Booking pending = booking(10L, BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusDays(5));
        pending.setPaymentDeadline(LocalDateTime.now().minusMinutes(1));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> bookingService.confirm(10L,
                new ConfirmPaymentRequest("CREDIT_CARD", "1234", null)))
                .isInstanceOf(PaymentDeadlineExceededException.class);

        verify(paymentClient, never()).process(any(PaymentRequestDto.class));
    }

    @Test
    void confirm_reservationDejaConfirmee_leveInvalidBookingStateException() {
        when(bookingRepository.findById(10L)).thenReturn(
                Optional.of(booking(10L, BookingStatus.CONFIRMED, LocalDateTime.now().plusDays(5))));

        assertThatThrownBy(() -> bookingService.confirm(10L,
                new ConfirmPaymentRequest("CREDIT_CARD", "1234", null)))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    // --- Cas 4 : annulation ---

    @Test
    void shouldCancelBookingAndRefund_whenWithinDeadline() {
        // Given : une reservation confirmee dont le cours est dans 5 jours
        Booking confirmed = booking(10L, BookingStatus.CONFIRMED, LocalDateTime.now().plusDays(5));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(confirmed));
        when(paymentClient.getByBookingId(10L)).thenReturn(payment(7L, "SUCCESS"));
        when(paymentClient.refund(7L)).thenReturn(payment(7L, "REFUNDED"));
        givenSaveReturnsEntity();

        // When : annulation
        BookingResponse result = bookingService.cancel(10L);

        // Then : annulee, remboursee, places rendues
        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.getCancellationDate()).isNotNull();
        verify(paymentClient).refund(7L);
        verify(classClient).decrement(101L, 2);
        verify(notificationPublisher).bookingCancelled(any(Booking.class), any(String.class));
    }

    @Test
    void cancel_reservationImpayee_libereLesPlacesSansRemboursement() {
        Booking pending = booking(10L, BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusDays(5));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(pending));
        givenSaveReturnsEntity();

        BookingResponse result = bookingService.cancel(10L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(classClient).decrement(101L, 2);
        verify(paymentClient, never()).refund(anyLong());
    }

    @Test
    void cancel_horsDelai_leveCancellationDeadlineExceededException() {
        // Cours dans 12h : la limite d'annulation (cours - 24h) est deja passee
        Booking confirmed = booking(10L, BookingStatus.CONFIRMED, LocalDateTime.now().plusHours(12));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> bookingService.cancel(10L))
                .isInstanceOf(CancellationDeadlineExceededException.class);

        verify(classClient, never()).decrement(anyLong(), anyInt());
        verify(paymentClient, never()).refund(anyLong());
    }

    @Test
    void cancel_reservationDejaAnnulee_leveInvalidBookingStateException() {
        when(bookingRepository.findById(10L)).thenReturn(
                Optional.of(booking(10L, BookingStatus.CANCELLED, LocalDateTime.now().plusDays(5))));

        assertThatThrownBy(() -> bookingService.cancel(10L))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void cancel_aucunPaiementTrouve_annuleQuandMeme() {
        Booking confirmed = booking(10L, BookingStatus.CONFIRMED, LocalDateTime.now().plusDays(5));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(confirmed));
        when(paymentClient.getByBookingId(10L)).thenReturn(null);
        givenSaveReturnsEntity();

        assertThat(bookingService.cancel(10L).getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(paymentClient, never()).refund(anyLong());
        verify(classClient).decrement(101L, 2);
    }

    // --- Cloture et scheduler ---

    @Test
    void complete_reservationConfirmee_passeEnCompleted() {
        Booking confirmed = booking(10L, BookingStatus.CONFIRMED, LocalDateTime.now().minusDays(1));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(confirmed));
        givenSaveReturnsEntity();

        assertThat(bookingService.complete(10L).getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void complete_reservationNonConfirmee_leveInvalidBookingStateException() {
        when(bookingRepository.findById(10L)).thenReturn(
                Optional.of(booking(10L, BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusDays(5))));

        assertThatThrownBy(() -> bookingService.complete(10L))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void cancelExpiredBookings_annuleEtLibereLesPlaces() {
        Booking expired = booking(10L, BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusDays(5));
        expired.setPaymentDeadline(LocalDateTime.now().minusHours(2));
        when(bookingRepository.findByStatusAndPaymentDeadlineBefore(eq(BookingStatus.PENDING_PAYMENT),
                any(LocalDateTime.class))).thenReturn(List.of(expired));
        givenSaveReturnsEntity();

        assertThat(bookingService.cancelExpiredBookings()).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(classClient).decrement(101L, 2);
    }

    @Test
    void cancelExpiredBookings_unEchecNInterrompPasLeLot() {
        Booking first = booking(10L, BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusDays(5));
        Booking second = booking(11L, BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusDays(6));
        second.setClassId(202L);
        when(bookingRepository.findByStatusAndPaymentDeadlineBefore(eq(BookingStatus.PENDING_PAYMENT),
                any(LocalDateTime.class))).thenReturn(List.of(first, second));
        when(classClient.decrement(101L, 2)).thenThrow(new IllegalStateException("class-service KO"));
        givenSaveReturnsEntity();

        assertThat(bookingService.cancelExpiredBookings()).isEqualTo(1);
        assertThat(first.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(second.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void sendClassReminders_marqueLeRappelPourNePasLeRepeter() {
        Booking upcoming = booking(10L, BookingStatus.CONFIRMED, LocalDateTime.now().plusHours(20));
        when(bookingRepository.findByStatusAndReminderSentFalseAndClassDateBetween(
                eq(BookingStatus.CONFIRMED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(upcoming));
        givenSaveReturnsEntity();

        assertThat(bookingService.sendClassReminders()).isEqualTo(1);
        assertThat(upcoming.getReminderSent()).isTrue();
        verify(notificationPublisher).bookingReminder(upcoming);
    }
}

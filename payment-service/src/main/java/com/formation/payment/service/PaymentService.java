package com.formation.payment.service;

import com.formation.payment.dto.PaymentRequest;
import com.formation.payment.dto.PaymentResponse;
import com.formation.payment.exception.PaymentNotFoundException;
import com.formation.payment.exception.PaymentNotRefundableException;
import com.formation.payment.model.Payment;
import com.formation.payment.model.PaymentStatus;
import com.formation.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /**
     * Simulation imposee par le cahier des charges : le prestataire accepte les paiements
     * strictement inferieurs a 100 euros et refuse les autres.
     */
    private static final BigDecimal ACCEPTANCE_THRESHOLD = new BigDecimal("100.00");
    private static final String FAILURE_REASON =
            "Paiement refuse par le prestataire : montant superieur ou egal a 100.00 EUR";

    private static final String REFERENCE_PREFIX = "PAY-";
    private static final String REFERENCE_ALPHABET = "ABCDEFGHIJKLMNPQRSTUVWXYZ123456789";
    private static final int REFERENCE_LENGTH = 5;
    private static final int MAX_REFERENCE_ATTEMPTS = 10;

    private final PaymentRepository paymentRepository;
    private final SecureRandom random = new SecureRandom();

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Traite un paiement. Un refus est un resultat metier persiste (status FAILED), pas une
     * erreur HTTP : booking-service a besoin de la trace de la tentative.
     */
    @Transactional
    public PaymentResponse process(PaymentRequest request) {
        boolean accepted = request.getAmount().compareTo(ACCEPTANCE_THRESHOLD) < 0;

        Payment payment = new Payment(generateReference(), request.getBookingId(),
                request.getBookingReference(), request.getUserId(), request.getAmount(),
                request.getPaymentMethod(),
                request.getPaymentMethod().isCard() ? request.getCardLastFour() : null,
                accepted ? resolveTransactionId(request) : null,
                LocalDateTime.now(),
                accepted ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);

        Payment saved = paymentRepository.save(payment);

        log.info("Paiement {} pour la reservation {} : {} ({} EUR)", saved.getPaymentReference(),
                saved.getBookingId(), saved.getStatus(), saved.getAmount());

        return PaymentMapper.toResponse(saved, FAILURE_REASON);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findByBookingId(Long bookingId) {
        return paymentRepository.findFirstByBookingIdOrderByPaymentDateDescIdDesc(bookingId)
                .map(payment -> PaymentMapper.toResponse(payment, FAILURE_REASON))
                .orElseThrow(() -> PaymentNotFoundException.forBooking(bookingId));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> findByUserId(Long userId) {
        return paymentRepository.findByUserIdOrderByPaymentDateDesc(userId).stream()
                .map(payment -> PaymentMapper.toResponse(payment, FAILURE_REASON))
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(Long id) {
        return PaymentMapper.toResponse(getPaymentOrThrow(id), FAILURE_REASON);
    }

    /**
     * Rembourse un paiement encaisse. Le controle du delai d'annulation (24h avant le cours)
     * appartient a booking-service, seul a connaitre la date du cours.
     */
    @Transactional
    public PaymentResponse refund(Long id) {
        Payment payment = getPaymentOrThrow(id);

        if (!payment.isRefundable()) {
            throw new PaymentNotRefundableException(id, payment.getStatus());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundDate(LocalDateTime.now());

        log.info("Remboursement du paiement {} ({} EUR)", payment.getPaymentReference(), payment.getAmount());

        return PaymentMapper.toResponse(paymentRepository.save(payment));
    }

    private Payment getPaymentOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private String resolveTransactionId(PaymentRequest request) {
        if (request.getTransactionId() != null && !request.getTransactionId().isBlank()) {
            return request.getTransactionId();
        }
        return "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String generateReference() {
        for (int attempt = 0; attempt < MAX_REFERENCE_ATTEMPTS; attempt++) {
            StringBuilder reference = new StringBuilder(REFERENCE_PREFIX);
            for (int i = 0; i < REFERENCE_LENGTH; i++) {
                reference.append(REFERENCE_ALPHABET.charAt(random.nextInt(REFERENCE_ALPHABET.length())));
            }
            String candidate = reference.toString();
            if (!paymentRepository.existsByPaymentReference(candidate)) {
                return candidate;
            }
        }
        // Repli sur une reference forcement unique plutot que d'echouer le paiement
        return REFERENCE_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

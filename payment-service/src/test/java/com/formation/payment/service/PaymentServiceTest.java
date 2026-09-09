package com.formation.payment.service;

import com.formation.payment.dto.PaymentRequest;
import com.formation.payment.dto.PaymentResponse;
import com.formation.payment.exception.PaymentNotFoundException;
import com.formation.payment.exception.PaymentNotRefundableException;
import com.formation.payment.model.Payment;
import com.formation.payment.model.PaymentMethod;
import com.formation.payment.model.PaymentStatus;
import com.formation.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private void givenSaveReturnsEntity() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(1L);
            }
            return payment;
        });
    }

    private PaymentRequest request(String amount) {
        return new PaymentRequest(10L, "BK-ABC12", 1L, new BigDecimal(amount),
                PaymentMethod.CREDIT_CARD, "1234", null);
    }

    private Payment payment(Long id, PaymentStatus status) {
        Payment payment = new Payment("PAY-XY12Z", 10L, "BK-ABC12", 1L, new BigDecimal("30.00"),
                PaymentMethod.CREDIT_CARD, "1234", "txn_123456", LocalDateTime.now(), status);
        payment.setId(id);
        return payment;
    }

    @Test
    void process_montantInferieurA100_accepteLePaiement() {
        givenSaveReturnsEntity();

        PaymentResponse result = paymentService.process(request("30.00"));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getTransactionId()).isNotBlank();
        assertThat(result.getPaymentReference()).startsWith("PAY-");
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    void process_montantEgalA100_refuseLePaiement() {
        givenSaveReturnsEntity();

        PaymentResponse result = paymentService.process(request("100.00"));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getTransactionId()).isNull();
        assertThat(result.getFailureReason()).contains("100.00");
    }

    @Test
    void process_montantSuperieurA100_refuseLePaiement() {
        givenSaveReturnsEntity();

        PaymentResponse result = paymentService.process(request("150.00"));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void process_conserveLeTransactionIdFourniParLAppelant() {
        givenSaveReturnsEntity();

        PaymentRequest request = request("30.00");
        request.setTransactionId("txn_fourni_par_le_client");

        assertThat(paymentService.process(request).getTransactionId())
                .isEqualTo("txn_fourni_par_le_client");
    }

    @Test
    void process_paypal_nEnregistrePasDeCardLastFour() {
        givenSaveReturnsEntity();

        PaymentRequest request = request("30.00");
        request.setPaymentMethod(PaymentMethod.PAYPAL);

        assertThat(paymentService.process(request).getCardLastFour()).isNull();
    }

    @Test
    void refund_paiementEncaisse_passeEnRefunded() {
        Payment payment = payment(1L, PaymentStatus.SUCCESS);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        givenSaveReturnsEntity();

        PaymentResponse result = paymentService.refund(1L);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(result.getRefundDate()).isNotNull();
    }

    @Test
    void refund_paiementRefuse_levePaymentNotRefundableException() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment(1L, PaymentStatus.FAILED)));

        assertThatThrownBy(() -> paymentService.refund(1L))
                .isInstanceOf(PaymentNotRefundableException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    void refund_paiementDejaRembourse_levePaymentNotRefundableException() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment(1L, PaymentStatus.REFUNDED)));

        assertThatThrownBy(() -> paymentService.refund(1L))
                .isInstanceOf(PaymentNotRefundableException.class);
    }

    @Test
    void findByBookingId_aucunPaiement_levePaymentNotFoundException() {
        when(paymentRepository.findFirstByBookingIdOrderByPaymentDateDescIdDesc(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.findByBookingId(99L))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("reservation 99");
    }
}

package com.formation.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.formation.payment.dto.PaymentRequest;
import com.formation.payment.model.Payment;
import com.formation.payment.model.PaymentMethod;
import com.formation.payment.model.PaymentStatus;
import com.formation.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PaymentRepository paymentRepository;

    private final AtomicInteger referenceSequence = new AtomicInteger();

    @BeforeEach
    void cleanDatabase() {
        paymentRepository.deleteAll();
    }

    private PaymentRequest request(String amount) {
        return new PaymentRequest(10L, "BK-ABC12", 1L, new BigDecimal(amount),
                PaymentMethod.CREDIT_CARD, "1234", null);
    }

    /** paymentReference est unique en base : chaque paiement persiste recoit la sienne. */
    private Payment persistPayment(Long bookingId, String amount, PaymentStatus status) {
        String reference = String.format("PAY-T%04d", referenceSequence.incrementAndGet());
        return paymentRepository.save(new Payment(reference, bookingId, "BK-ABC12", 1L,
                new BigDecimal(amount), PaymentMethod.CREDIT_CARD, "1234", "txn_123456",
                LocalDateTime.now(), status));
    }

    @Test
    void process_montantAccepte_retourne201EtStatutSuccess() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("45.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentReference").exists())
                .andExpect(jsonPath("$.transactionId").exists());
    }

    @Test
    void process_montantRefuse_retourne201EtStatutFailed() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("120.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").exists());
    }

    @Test
    void process_cardLastFourInvalide_retourne400() throws Exception {
        PaymentRequest request = request("45.00");
        request.setCardLastFour("12");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cardLastFour").exists());
    }

    @Test
    void getByBookingId_retourneLaTentativeLaPlusRecente() throws Exception {
        persistPayment(10L, "45.00", PaymentStatus.FAILED);
        Payment latest = persistPayment(10L, "45.00", PaymentStatus.SUCCESS);

        mockMvc.perform(get("/api/payments/booking/{bookingId}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(latest.getId()))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void getByBookingId_aucunPaiement_retourne404() throws Exception {
        mockMvc.perform(get("/api/payments/booking/{bookingId}", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void refund_paiementEncaisse_retourne200EtStatutRefunded() throws Exception {
        Payment payment = persistPayment(10L, "45.00", PaymentStatus.SUCCESS);

        mockMvc.perform(post("/api/payments/{id}/refund", payment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refundDate").exists());

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refund_paiementRefuse_retourne409() throws Exception {
        Payment payment = persistPayment(10L, "120.00", PaymentStatus.FAILED);

        mockMvc.perform(post("/api/payments/{id}/refund", payment.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void getByUserId_retourneLHistorique() throws Exception {
        persistPayment(10L, "45.00", PaymentStatus.SUCCESS);
        persistPayment(11L, "30.00", PaymentStatus.SUCCESS);

        mockMvc.perform(get("/api/payments/user/{userId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}

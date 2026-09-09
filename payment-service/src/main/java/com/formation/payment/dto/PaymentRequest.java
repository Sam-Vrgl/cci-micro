package com.formation.payment.dto;

import com.formation.payment.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public class PaymentRequest {

    @NotNull(message = "L'identifiant de reservation est obligatoire")
    private Long bookingId;

    private String bookingReference;

    @NotNull(message = "L'identifiant utilisateur est obligatoire")
    private Long userId;

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.00", message = "Le montant ne peut pas etre negatif")
    private BigDecimal amount;

    @NotNull(message = "Le moyen de paiement est obligatoire")
    private PaymentMethod paymentMethod;

    @Pattern(regexp = "\\d{4}", message = "cardLastFour doit contenir exactement 4 chiffres")
    private String cardLastFour;

    /** Identifiant fourni par le prestataire externe ; genere par le service s'il est absent. */
    private String transactionId;

    public PaymentRequest() {}

    public PaymentRequest(Long bookingId, String bookingReference, Long userId, BigDecimal amount,
                          PaymentMethod paymentMethod, String cardLastFour, String transactionId) {
        this.bookingId = bookingId;
        this.bookingReference = bookingReference;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.cardLastFour = cardLastFour;
        this.transactionId = transactionId;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getCardLastFour() {
        return cardLastFour;
    }

    public void setCardLastFour(String cardLastFour) {
        this.cardLastFour = cardLastFour;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
}

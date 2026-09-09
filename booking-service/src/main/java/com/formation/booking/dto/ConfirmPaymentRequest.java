package com.formation.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class ConfirmPaymentRequest {

    @NotNull(message = "Le moyen de paiement est obligatoire")
    @Pattern(regexp = "CREDIT_CARD|DEBIT_CARD|PAYPAL|STRIPE",
            message = "Moyen de paiement inconnu (CREDIT_CARD, DEBIT_CARD, PAYPAL, STRIPE)")
    private String paymentMethod;

    @Pattern(regexp = "\\d{4}", message = "cardLastFour doit contenir exactement 4 chiffres")
    private String cardLastFour;

    private String transactionId;

    public ConfirmPaymentRequest() {}

    public ConfirmPaymentRequest(String paymentMethod, String cardLastFour, String transactionId) {
        this.paymentMethod = paymentMethod;
        this.cardLastFour = cardLastFour;
        this.transactionId = transactionId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
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

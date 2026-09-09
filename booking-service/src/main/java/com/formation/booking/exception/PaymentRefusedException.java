package com.formation.booking.exception;

public class PaymentRefusedException extends RuntimeException {

    private final String paymentReference;

    public PaymentRefusedException(Long bookingId, String paymentReference, String reason) {
        super("Paiement refuse pour la reservation " + bookingId
                + (reason != null ? " : " + reason : "")
                + ". La reservation reste en attente de paiement jusqu'a son echeance.");
        this.paymentReference = paymentReference;
    }

    public String getPaymentReference() {
        return paymentReference;
    }
}

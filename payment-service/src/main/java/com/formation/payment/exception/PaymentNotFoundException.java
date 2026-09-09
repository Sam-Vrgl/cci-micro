package com.formation.payment.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long id) {
        super("Paiement introuvable : " + id);
    }

    public static PaymentNotFoundException forBooking(Long bookingId) {
        return new PaymentNotFoundException("Aucun paiement pour la reservation " + bookingId);
    }

    private PaymentNotFoundException(String message) {
        super(message);
    }
}

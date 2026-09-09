package com.formation.booking.exception;

import java.time.LocalDateTime;

public class PaymentDeadlineExceededException extends RuntimeException {
    public PaymentDeadlineExceededException(Long bookingId, LocalDateTime deadline) {
        super("Paiement expire pour la reservation " + bookingId + " : le delai courait jusqu'au " + deadline);
    }
}

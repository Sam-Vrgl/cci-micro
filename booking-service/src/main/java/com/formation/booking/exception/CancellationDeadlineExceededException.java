package com.formation.booking.exception;

import java.time.LocalDateTime;

public class CancellationDeadlineExceededException extends RuntimeException {
    public CancellationDeadlineExceededException(Long bookingId, LocalDateTime deadline) {
        super("Annulation non autorisee pour la reservation " + bookingId
                + " : le delai courait jusqu'au " + deadline + " (24h avant le cours)");
    }
}

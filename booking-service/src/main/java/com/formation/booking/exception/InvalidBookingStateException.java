package com.formation.booking.exception;

import com.formation.booking.model.BookingStatus;

public class InvalidBookingStateException extends RuntimeException {
    public InvalidBookingStateException(Long bookingId, BookingStatus current, String expected) {
        super("Operation impossible sur la reservation " + bookingId + " : statut " + current
                + ", attendu " + expected);
    }
}

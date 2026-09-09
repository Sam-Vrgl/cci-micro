package com.formation.booking.exception;

public class ClassNotFoundForBookingException extends RuntimeException {
    public ClassNotFoundForBookingException(Long classId) {
        super("Le cours " + classId + " n'existe pas : reservation impossible");
    }
}

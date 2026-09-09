package com.formation.booking.exception;

public class NoSpotsAvailableException extends RuntimeException {
    public NoSpotsAvailableException(Long classId) {
        super("Plus de places disponibles pour ce cours (" + classId + ")");
    }
}

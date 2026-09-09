package com.formation.booking.exception;

public class ClassNotBookableException extends RuntimeException {
    public ClassNotBookableException(Long classId, String status) {
        super("Le cours " + classId + " n'est pas reservable : statut " + status);
    }
}

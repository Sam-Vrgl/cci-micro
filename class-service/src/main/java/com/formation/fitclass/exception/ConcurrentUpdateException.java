package com.formation.fitclass.exception;

public class ConcurrentUpdateException extends RuntimeException {
    public ConcurrentUpdateException(Long classId, Throwable cause) {
        super("Trop de mises a jour concurrentes sur le cours " + classId + ", veuillez reessayer", cause);
    }
}

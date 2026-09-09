package com.formation.fitclass.exception;

import com.formation.fitclass.model.ClassStatus;

public class InvalidClassStateException extends RuntimeException {

    public InvalidClassStateException(Long classId, ClassStatus status) {
        super("Operation impossible sur le cours " + classId + " : statut " + status);
    }

    public InvalidClassStateException(String message) {
        super(message);
    }
}

package com.formation.fitclass.exception;

public class NoSpotsAvailableException extends RuntimeException {
    public NoSpotsAvailableException(Long classId, int availableSpots, int requestedSpots) {
        super("Plus de places disponibles pour le cours " + classId
                + " : " + requestedSpots + " place(s) demandee(s), " + availableSpots + " restante(s)");
    }
}

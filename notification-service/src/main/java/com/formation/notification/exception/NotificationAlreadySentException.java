package com.formation.notification.exception;

public class NotificationAlreadySentException extends RuntimeException {
    public NotificationAlreadySentException(Long id) {
        super("La notification " + id + " a deja ete envoyee : rien a reessayer");
    }
}

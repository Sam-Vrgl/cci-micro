package com.formation.booking.service;

import com.formation.booking.client.NotificationClient;
import com.formation.booking.dto.NotificationRequestDto;
import com.formation.booking.model.Booking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.format.DateTimeFormatter;

/**
 * Redige et emet les notifications de la saga. Aucune de ces emissions ne peut faire echouer
 * l'operation metier qui les declenche : le repli du client journalise, et cette classe absorbe
 * en plus toute erreur imprevue.
 */
@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");

    private final NotificationClient notificationClient;

    public NotificationPublisher(NotificationClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    public void bookingConfirmation(Booking booking) {
        send(booking, "BOOKING_CONFIRMATION",
                "Reservation " + booking.getBookingReference() + " en attente de paiement",
                "Bonjour " + booking.getUserName() + ", votre reservation est en attente de paiement. "
                        + "Payez avant " + format(booking.getPaymentDeadline()) + " pour confirmer vos "
                        + booking.getNumberOfSpots() + " place(s) au cours " + booking.getClassName()
                        + " du " + format(booking.getClassDate()) + ".");
    }

    public void paymentConfirmation(Booking booking, String paymentReference) {
        send(booking, "PAYMENT_CONFIRMATION",
                "Paiement confirme pour la reservation " + booking.getBookingReference(),
                "Bonjour " + booking.getUserName() + ", votre paiement de " + booking.getTotalAmount()
                        + " EUR (reference " + paymentReference + ") est confirme. Rendez-vous au cours "
                        + booking.getClassName() + " du " + format(booking.getClassDate()) + ".");
    }

    public void bookingCancelled(Booking booking, String reason) {
        send(booking, "BOOKING_CANCELLED",
                "Reservation " + booking.getBookingReference() + " annulee",
                "Bonjour " + booking.getUserName() + ", votre reservation pour le cours "
                        + booking.getClassName() + " du " + format(booking.getClassDate())
                        + " a ete annulee. Motif : " + reason + ".");
    }

    public void bookingReminder(Booking booking) {
        send(booking, "BOOKING_REMINDER",
                "Rappel : votre cours " + booking.getClassName() + " a lieu demain",
                "Bonjour " + booking.getUserName() + ", votre cours " + booking.getClassName()
                        + " avec " + booking.getInstructor() + " a lieu le "
                        + format(booking.getClassDate()) + ". A demain !");
    }

    private void send(Booking booking, String type, String subject, String content) {
        try {
            notificationClient.send(new NotificationRequestDto(booking.getUserId(),
                    booking.getUserEmail(), type, subject, content));
        } catch (RuntimeException ex) {
            // Filet de securite : notification-service n'est pas critique pour la saga
            log.warn("Notification {} non emise pour la reservation {} : {}", type,
                    booking.getBookingReference(), ex.toString());
        }
    }

    private String format(java.time.LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_FORMAT) : "date inconnue";
    }
}

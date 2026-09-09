package com.formation.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Taches planifiees de booking-service. Les crons sont externalises dans config-repo
 * (fitconnect.scheduler.*) pour pouvoir etre accelerees en demonstration.
 */
@Component
public class BookingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingScheduler.class);

    private final BookingService bookingService;

    public BookingScheduler(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Toutes les 5 minutes : annule les reservations impayees et libere leurs places. */
    @Scheduled(cron = "${fitconnect.scheduler.expiration-cron:0 */5 * * * *}")
    public void expirePendingPayments() {
        try {
            bookingService.cancelExpiredBookings();
        } catch (RuntimeException ex) {
            // Une tache planifiee qui leve cesse d'etre replanifiee sur certains ordonnanceurs :
            // on journalise et on rendra la main proprement.
            log.error("Passage d'expiration des paiements interrompu", ex);
        }
    }

    /** Toutes les heures : previent les participants dont le cours a lieu dans moins de 24h. */
    @Scheduled(cron = "${fitconnect.scheduler.reminder-cron:0 0 * * * *}")
    public void sendReminders() {
        try {
            bookingService.sendClassReminders();
        } catch (RuntimeException ex) {
            log.error("Passage d'envoi des rappels interrompu", ex);
        }
    }
}

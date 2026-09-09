/**
 * Exceptions metier de booking-service et leur traduction en codes HTTP par
 * {@link com.formation.booking.exception.GlobalExceptionHandler} :
 *
 * <ul>
 *   <li>{@link com.formation.booking.exception.BookingNotFoundException} : 404</li>
 *   <li>{@link com.formation.booking.exception.ClassNotFoundForBookingException} : 400, le cours
 *       reference n'existe pas</li>
 *   <li>{@link com.formation.booking.exception.NoSpotsAvailableException} : 409, surreservation</li>
 *   <li>{@link com.formation.booking.exception.ClassNotBookableException} : 409, cours annule ou
 *       termine</li>
 *   <li>{@link com.formation.booking.exception.InvalidBookingStateException} : 409, transition de
 *       statut impossible</li>
 *   <li>{@link com.formation.booking.exception.PaymentDeadlineExceededException} : 409, paiement
 *       expire</li>
 *   <li>{@link com.formation.booking.exception.CancellationDeadlineExceededException} : 409,
 *       annulation hors delai</li>
 *   <li>{@link com.formation.booking.exception.PaymentRefusedException} : 402, paiement refuse par
 *       le prestataire</li>
 *   <li>{@link com.formation.booking.exception.RemoteServiceUnavailableException} : 503, circuit
 *       ouvert ou service injoignable</li>
 * </ul>
 */
package com.formation.booking.exception;

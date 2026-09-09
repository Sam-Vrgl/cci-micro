package com.formation.booking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ApiError> handleBookingNotFound(BookingNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Le cours reference par la demande n'existe pas : la requete du client est fautive. */
    @ExceptionHandler(ClassNotFoundForBookingException.class)
    public ResponseEntity<ApiError> handleClassNotFound(ClassNotFoundForBookingException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({NoSpotsAvailableException.class, ClassNotBookableException.class,
            InvalidBookingStateException.class, PaymentDeadlineExceededException.class,
            CancellationDeadlineExceededException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Le prestataire a refuse le paiement. 402 distingue ce cas d'un conflit d'etat : la
     * reservation reste valide et payable jusqu'a son echeance.
     */
    @ExceptionHandler(PaymentRefusedException.class)
    public ResponseEntity<ApiError> handlePaymentRefused(PaymentRefusedException ex) {
        return build(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
    }

    /** Circuit ouvert ou service injoignable : la saga s'est arretee sans effet de bord. */
    @ExceptionHandler(RemoteServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleServiceUnavailable(RemoteServiceUnavailableException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Erreur de validation", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), message));
    }
}

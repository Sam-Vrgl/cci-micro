package com.formation.booking.client;

import com.formation.booking.dto.PaymentRequestDto;
import com.formation.booking.dto.PaymentResponseDto;
import com.formation.booking.exception.RemoteServiceUnavailableException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Repli du client payment-service.
 *
 * <p>Le traitement d'un paiement est critique : toute erreur arrete la saga, car un paiement dont
 * on ignore l'issue ne doit jamais confirmer une reservation. En revanche la consultation et le
 * remboursement renvoient null sur une reponse 404/409 : l'absence de paiement, ou un paiement
 * deja rembourse, ne doit pas empecher l'annulation de la reservation.
 */
@Component
public class PaymentClientFallbackFactory implements FallbackFactory<PaymentClient> {

    private static final Logger log = LoggerFactory.getLogger(PaymentClientFallbackFactory.class);

    @Override
    public PaymentClient create(Throwable cause) {
        return new PaymentClient() {

            @Override
            public PaymentResponseDto process(PaymentRequestDto request) {
                throw unavailable(cause);
            }

            @Override
            public PaymentResponseDto getByBookingId(Long bookingId) {
                if (statusOf(cause) == 404) {
                    log.info("Aucun paiement enregistre pour la reservation {}", bookingId);
                    return null;
                }
                throw unavailable(cause);
            }

            @Override
            public PaymentResponseDto refund(Long id) {
                int status = statusOf(cause);
                if (status == 404 || status == 409) {
                    log.warn("Remboursement du paiement {} impossible (HTTP {}) : l'annulation se poursuit",
                            id, status);
                    return null;
                }
                throw unavailable(cause);
            }
        };
    }

    private int statusOf(Throwable cause) {
        return cause instanceof FeignException feignException ? feignException.status() : -1;
    }

    private RemoteServiceUnavailableException unavailable(Throwable cause) {
        log.error("Appel a payment-service en echec", cause);
        return new RemoteServiceUnavailableException("payment-service", cause);
    }
}

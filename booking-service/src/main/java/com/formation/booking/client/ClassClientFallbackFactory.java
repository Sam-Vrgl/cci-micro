package com.formation.booking.client;

import com.formation.booking.dto.FitnessClassDto;
import com.formation.booking.exception.ClassNotFoundForBookingException;
import com.formation.booking.exception.NoSpotsAvailableException;
import com.formation.booking.exception.RemoteServiceUnavailableException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Repli du client class-service. Le circuit breaker route ici toutes les erreurs, y compris les
 * reponses metier 404/409 : ce repli les retraduit en exceptions du domaine, et ne signale une
 * indisponibilite que pour les pannes reelles (connexion refusee, timeout, circuit ouvert).
 *
 * <p>class-service est critique : aucune valeur de repli n'est inventee, la saga s'arrete.
 */
@Component
public class ClassClientFallbackFactory implements FallbackFactory<ClassClient> {

    private static final Logger log = LoggerFactory.getLogger(ClassClientFallbackFactory.class);

    @Override
    public ClassClient create(Throwable cause) {
        return new ClassClient() {

            @Override
            public FitnessClassDto getById(Long id) {
                throw translate(cause, id);
            }

            @Override
            public FitnessClassDto increment(Long id, int spots) {
                throw translate(cause, id);
            }

            @Override
            public FitnessClassDto decrement(Long id, int spots) {
                throw translate(cause, id);
            }
        };
    }

    private RuntimeException translate(Throwable cause, Long classId) {
        if (cause instanceof FeignException feignException) {
            return switch (feignException.status()) {
                case 404 -> new ClassNotFoundForBookingException(classId);
                case 409 -> new NoSpotsAvailableException(classId);
                default -> unavailable(cause);
            };
        }
        return unavailable(cause);
    }

    private RemoteServiceUnavailableException unavailable(Throwable cause) {
        log.error("Appel a class-service en echec", cause);
        return new RemoteServiceUnavailableException("class-service", cause);
    }
}

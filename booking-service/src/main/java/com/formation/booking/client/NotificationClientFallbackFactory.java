package com.formation.booking.client;

import com.formation.booking.dto.NotificationRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Repli du client notification-service. Contrairement aux deux autres, ce service n'est pas
 * critique : une notification perdue ne doit jamais annuler une reservation payee. Le repli se
 * contente donc de journaliser, et la saga continue.
 */
@Component
public class NotificationClientFallbackFactory implements FallbackFactory<NotificationClient> {

    private static final Logger log = LoggerFactory.getLogger(NotificationClientFallbackFactory.class);

    @Override
    public NotificationClient create(Throwable cause) {
        return request -> log.warn("Notification {} non envoyee a {} : {}", request.getType(),
                request.getEmail(), cause.toString());
    }
}

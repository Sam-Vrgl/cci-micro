package com.formation.notification.service;

import com.formation.notification.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * Envoi simule : le TP ne branche pas de vrai fournisseur SMTP/SMS. La remise echoue lorsque
 * l'adresse du destinataire est absente ou mal formee, ce qui rend le statut FAILED et le
 * endpoint /retry reellement testables.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * @return null si la remise a reussi, sinon le motif de l'echec.
     */
    public String send(Notification notification) {
        String email = notification.getEmail();

        if (email == null || email.isBlank()) {
            return "Adresse du destinataire absente";
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return "Adresse du destinataire invalide : " + email;
        }

        log.info("[EMAIL SIMULE] destinataire={} type={} sujet=\"{}\" | {}", email,
                notification.getType(), notification.getSubject(), notification.getContent());
        return null;
    }
}

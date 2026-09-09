package com.formation.notification.dto;

import com.formation.notification.model.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class NotificationRequest {

    @NotNull(message = "L'identifiant utilisateur est obligatoire")
    private Long userId;

    /** Non annote : une adresse absente ou invalide doit produire une notification FAILED,
     *  pas un rejet de la requete — l'appelant est un service, pas un formulaire. */
    private String email;

    @NotNull(message = "Le type de notification est obligatoire")
    private NotificationType type;

    @NotBlank(message = "Le sujet est obligatoire")
    private String subject;

    @NotBlank(message = "Le contenu est obligatoire")
    private String content;

    public NotificationRequest() {}

    public NotificationRequest(Long userId, String email, NotificationType type, String subject,
                               String content) {
        this.userId = userId;
        this.email = email;
        this.type = type;
        this.subject = subject;
        this.content = content;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

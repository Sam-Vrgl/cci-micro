package com.formation.notification.service;

import com.formation.notification.dto.NotificationRequest;
import com.formation.notification.dto.NotificationResponse;
import com.formation.notification.model.Notification;

public final class NotificationMapper {

    private NotificationMapper() {}

    public static Notification toEntity(NotificationRequest request) {
        return new Notification(request.getUserId(), request.getEmail(), request.getType(),
                request.getSubject(), request.getContent());
    }

    public static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getUserId(),
                notification.getEmail(), notification.getType(), notification.getSubject(),
                notification.getContent(), notification.getSentDate(), notification.getStatus(),
                notification.getCreatedDate(), notification.getAttempts(),
                notification.getFailureReason());
    }
}

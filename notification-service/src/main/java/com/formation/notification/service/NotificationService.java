package com.formation.notification.service;

import com.formation.notification.dto.NotificationRequest;
import com.formation.notification.dto.NotificationResponse;
import com.formation.notification.exception.NotificationAlreadySentException;
import com.formation.notification.exception.NotificationNotFoundException;
import com.formation.notification.model.Notification;
import com.formation.notification.model.NotificationStatus;
import com.formation.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;

    public NotificationService(NotificationRepository notificationRepository, EmailSender emailSender) {
        this.notificationRepository = notificationRepository;
        this.emailSender = emailSender;
    }

    /**
     * Persiste la notification puis tente la remise. Un echec de remise n'est pas propage a
     * l'appelant : la notification reste en base en FAILED et pourra etre rejouee via /retry.
     */
    @Transactional
    public NotificationResponse send(NotificationRequest request) {
        Notification notification = notificationRepository.save(NotificationMapper.toEntity(request));
        return NotificationMapper.toResponse(attemptDelivery(notification));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findByUserId(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedDateDesc(userId).stream()
                .map(NotificationMapper::toResponse)
                .toList();
    }

    /** Notifications a reprendre : jamais parties, ou parties en erreur. */
    @Transactional(readOnly = true)
    public List<NotificationResponse> findPending() {
        return notificationRepository
                .findByStatusInOrderByCreatedDateAsc(
                        List.of(NotificationStatus.PENDING, NotificationStatus.FAILED))
                .stream()
                .map(NotificationMapper::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse retry(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));

        if (notification.getStatus() == NotificationStatus.SENT) {
            throw new NotificationAlreadySentException(id);
        }

        return NotificationMapper.toResponse(attemptDelivery(notification));
    }

    private Notification attemptDelivery(Notification notification) {
        String failureReason = emailSender.send(notification);

        if (failureReason == null) {
            notification.markSent();
        } else {
            notification.markFailed(failureReason);
        }

        return notificationRepository.save(notification);
    }
}

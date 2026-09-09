package com.formation.notification.service;

import com.formation.notification.dto.NotificationRequest;
import com.formation.notification.dto.NotificationResponse;
import com.formation.notification.exception.NotificationAlreadySentException;
import com.formation.notification.exception.NotificationNotFoundException;
import com.formation.notification.model.Notification;
import com.formation.notification.model.NotificationStatus;
import com.formation.notification.model.NotificationType;
import com.formation.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService() {
        // EmailSender n'est pas simule : sa regle de validation d'adresse fait partie du
        // comportement teste (SENT vs FAILED).
        return new NotificationService(notificationRepository, new EmailSender());
    }

    private void givenSaveReturnsEntity() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getId() == null) {
                notification.setId(1L);
            }
            return notification;
        });
    }

    private NotificationRequest request(String email) {
        return new NotificationRequest(1L, email, NotificationType.BOOKING_CONFIRMATION,
                "Reservation en attente de paiement",
                "Votre reservation est en attente de paiement. Payez avant 18:00");
    }

    @Test
    void send_adresseValide_marqueLaNotificationSent() {
        givenSaveReturnsEntity();

        NotificationResponse result = notificationService().send(request("john@example.com"));

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.getSentDate()).isNotNull();
        assertThat(result.getAttempts()).isEqualTo(1);
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    void send_adresseInvalide_marqueLaNotificationFailedSansLeverDException() {
        givenSaveReturnsEntity();

        NotificationResponse result = notificationService().send(request("adresse-sans-arobase"));

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getSentDate()).isNull();
        assertThat(result.getFailureReason()).contains("invalide");
    }

    @Test
    void send_adresseAbsente_marqueLaNotificationFailed() {
        givenSaveReturnsEntity();

        NotificationResponse result = notificationService().send(request(null));

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getFailureReason()).contains("absente");
    }

    @Test
    void retry_notificationEnEchec_renvoieEtIncrementeLesTentatives() {
        Notification notification = new Notification(1L, "john@example.com",
                NotificationType.BOOKING_CONFIRMATION, "Sujet", "Contenu");
        notification.setId(1L);
        notification.markFailed("Adresse du destinataire absente");

        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        givenSaveReturnsEntity();

        NotificationResponse result = notificationService().retry(1L);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.getAttempts()).isEqualTo(2);
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    void retry_notificationDejaEnvoyee_leveNotificationAlreadySentException() {
        Notification notification = new Notification(1L, "john@example.com",
                NotificationType.BOOKING_CONFIRMATION, "Sujet", "Contenu");
        notification.setId(1L);
        notification.markSent();

        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService().retry(1L))
                .isInstanceOf(NotificationAlreadySentException.class);
    }

    @Test
    void retry_notificationInconnue_leveNotificationNotFoundException() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService().retry(99L))
                .isInstanceOf(NotificationNotFoundException.class);
    }
}

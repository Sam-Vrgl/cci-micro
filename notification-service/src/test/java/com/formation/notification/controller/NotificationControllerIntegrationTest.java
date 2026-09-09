package com.formation.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.formation.notification.dto.NotificationRequest;
import com.formation.notification.model.Notification;
import com.formation.notification.model.NotificationStatus;
import com.formation.notification.model.NotificationType;
import com.formation.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void cleanDatabase() {
        notificationRepository.deleteAll();
    }

    private NotificationRequest request(String email) {
        return new NotificationRequest(1L, email, NotificationType.BOOKING_CONFIRMATION,
                "Reservation en attente de paiement",
                "Votre reservation est en attente de paiement.");
    }

    @Test
    void send_adresseValide_retourne201EtStatutSent() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("john@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.type").value("BOOKING_CONFIRMATION"))
                .andExpect(jsonPath("$.sentDate").exists());
    }

    @Test
    void send_adresseInvalide_retourne201EtStatutFailed() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("pas-une-adresse"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").exists());
    }

    @Test
    void send_sujetAbsent_retourne400() throws Exception {
        NotificationRequest request = request("john@example.com");
        request.setSubject("  ");

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.subject").exists());
    }

    @Test
    void getPending_neRetourneQueLesNotificationsNonEnvoyees() throws Exception {
        Notification sent = new Notification(1L, "john@example.com",
                NotificationType.BOOKING_CONFIRMATION, "Envoyee", "Contenu");
        sent.markSent();
        notificationRepository.save(sent);

        Notification failed = new Notification(1L, "invalide",
                NotificationType.BOOKING_CANCELLED, "En echec", "Contenu");
        failed.markFailed("Adresse du destinataire invalide");
        notificationRepository.save(failed);

        mockMvc.perform(get("/api/notifications/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subject").value("En echec"));
    }

    @Test
    void retry_notificationEnEchec_repasseEnSentApresCorrectionDeLAdresse() throws Exception {
        Notification failed = new Notification(1L, "invalide",
                NotificationType.BOOKING_CANCELLED, "En echec", "Contenu");
        failed.markFailed("Adresse du destinataire invalide");
        Notification saved = notificationRepository.save(failed);

        saved.setEmail("john@example.com");
        notificationRepository.save(saved);

        mockMvc.perform(patch("/api/notifications/{id}/retry", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.attempts").value(2));

        assertThat(notificationRepository.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void retry_notificationDejaEnvoyee_retourne409() throws Exception {
        Notification sent = new Notification(1L, "john@example.com",
                NotificationType.BOOKING_CONFIRMATION, "Envoyee", "Contenu");
        sent.markSent();
        Notification saved = notificationRepository.save(sent);

        mockMvc.perform(patch("/api/notifications/{id}/retry", saved.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void getByUserId_retourneLHistorique() throws Exception {
        mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request("john@example.com"))));

        mockMvc.perform(get("/api/notifications/user/{userId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}

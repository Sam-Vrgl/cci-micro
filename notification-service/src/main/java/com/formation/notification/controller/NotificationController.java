package com.formation.notification.controller;

import com.formation.notification.dto.NotificationRequest;
import com.formation.notification.dto.NotificationResponse;
import com.formation.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Appele par les autres services, booking-service en particulier. */
    @PostMapping
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody NotificationRequest request) {
        NotificationResponse sent = notificationService.send(request);
        return ResponseEntity.created(URI.create("/api/notifications/" + sent.getId())).body(sent);
    }

    @GetMapping("/user/{userId}")
    public List<NotificationResponse> getByUserId(@PathVariable Long userId) {
        return notificationService.findByUserId(userId);
    }

    @GetMapping("/pending")
    public List<NotificationResponse> getPending() {
        return notificationService.findPending();
    }

    @PatchMapping("/{id}/retry")
    public NotificationResponse retry(@PathVariable Long id) {
        return notificationService.retry(id);
    }
}

package com.formation.notification.dto;

import com.formation.notification.model.NotificationStatus;
import com.formation.notification.model.NotificationType;
import java.time.LocalDateTime;

public class NotificationResponse {

    private Long id;
    private Long userId;
    private String email;
    private NotificationType type;
    private String subject;
    private String content;
    private LocalDateTime sentDate;
    private NotificationStatus status;
    private LocalDateTime createdDate;
    private Integer attempts;
    private String failureReason;

    public NotificationResponse() {}

    public NotificationResponse(Long id, Long userId, String email, NotificationType type,
                                String subject, String content, LocalDateTime sentDate,
                                NotificationStatus status, LocalDateTime createdDate, Integer attempts,
                                String failureReason) {
        this.id = id;
        this.userId = userId;
        this.email = email;
        this.type = type;
        this.subject = subject;
        this.content = content;
        this.sentDate = sentDate;
        this.status = status;
        this.createdDate = createdDate;
        this.attempts = attempts;
        this.failureReason = failureReason;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getSentDate() {
        return sentDate;
    }

    public void setSentDate(LocalDateTime sentDate) {
        this.sentDate = sentDate;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}

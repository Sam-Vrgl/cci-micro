package com.formation.booking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vue partielle du cours renvoyee par class-service. Les champs non utilises par la reservation
 * sont ignores : booking-service ne doit pas casser si class-service enrichit sa reponse.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FitnessClassDto {

    public static final String STATUS_SCHEDULED = "SCHEDULED";

    private Long id;
    private String name;
    private String instructor;
    private BigDecimal price;
    private LocalDateTime dateTime;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private Integer availableSpots;
    private String status;

    public FitnessClassDto() {}

    public boolean isScheduled() {
        return STATUS_SCHEDULED.equals(status);
    }

    public boolean hasAvailableSpots(int spots) {
        return availableSpots != null && availableSpots >= spots;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public Integer getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(Integer maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public Integer getCurrentParticipants() {
        return currentParticipants;
    }

    public void setCurrentParticipants(Integer currentParticipants) {
        this.currentParticipants = currentParticipants;
    }

    public Integer getAvailableSpots() {
        return availableSpots;
    }

    public void setAvailableSpots(Integer availableSpots) {
        this.availableSpots = availableSpots;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

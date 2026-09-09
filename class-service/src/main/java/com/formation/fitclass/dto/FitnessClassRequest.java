package com.formation.fitclass.dto;

import com.formation.fitclass.model.Category;
import com.formation.fitclass.model.ClassStatus;
import com.formation.fitclass.model.Level;
import com.formation.fitclass.validation.AllowedDuration;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FitnessClassRequest {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 3, message = "Le nom doit contenir au moins 3 caracteres")
    private String name;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    @NotBlank(message = "Le nom de l'instructeur est obligatoire")
    private String instructor;

    @NotBlank(message = "Le lieu de la salle est obligatoire")
    private String gymLocation;

    @NotNull(message = "La categorie est obligatoire")
    private Category category;

    @NotNull(message = "Le niveau est obligatoire")
    private Level level;

    @NotNull(message = "La duree est obligatoire")
    @AllowedDuration
    private Integer durationMinutes;

    @NotNull(message = "Le nombre maximum de participants est obligatoire")
    @Min(value = 5, message = "Le nombre maximum de participants doit etre au moins 5")
    @Max(value = 30, message = "Le nombre maximum de participants ne peut pas depasser 30")
    private Integer maxParticipants;

    @NotNull(message = "Le prix est obligatoire")
    @DecimalMin(value = "5.00", message = "Le prix doit etre au moins 5.00")
    private BigDecimal price;

    @NotNull(message = "La date du cours est obligatoire")
    @Future(message = "La date du cours doit etre dans le futur")
    private LocalDateTime dateTime;

    /** Optionnel : SCHEDULED par defaut a la creation, permet de reprogrammer ou cloturer un cours. */
    private ClassStatus status;

    public FitnessClassRequest() {}

    public FitnessClassRequest(String name, String description, String instructor, String gymLocation,
                               Category category, Level level, Integer durationMinutes,
                               Integer maxParticipants, BigDecimal price, LocalDateTime dateTime,
                               ClassStatus status) {
        this.name = name;
        this.description = description;
        this.instructor = instructor;
        this.gymLocation = gymLocation;
        this.category = category;
        this.level = level;
        this.durationMinutes = durationMinutes;
        this.maxParticipants = maxParticipants;
        this.price = price;
        this.dateTime = dateTime;
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public String getGymLocation() {
        return gymLocation;
    }

    public void setGymLocation(String gymLocation) {
        this.gymLocation = gymLocation;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Integer getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(Integer maxParticipants) {
        this.maxParticipants = maxParticipants;
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

    public ClassStatus getStatus() {
        return status;
    }

    public void setStatus(ClassStatus status) {
        this.status = status;
    }
}

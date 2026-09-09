package com.formation.fitclass.model;

import com.formation.fitclass.exception.NoSpotsAvailableException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fitness_classes")
public class FitnessClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Verrouillage optimiste : deux reservations simultanees sur le meme cours ne peuvent pas
     * ecrire currentParticipants a partir de la meme version. La seconde ecriture leve une
     * ObjectOptimisticLockingFailureException, rejouee par le service sur la version a jour.
     */
    @Version
    private Long version;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String instructor;

    @Column(nullable = false)
    private String gymLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Level level;

    @Column(nullable = false)
    private Integer durationMinutes;

    @Column(nullable = false)
    private Integer maxParticipants;

    @Column(nullable = false)
    private Integer currentParticipants;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private LocalDateTime dateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassStatus status;

    public FitnessClass() {}

    public FitnessClass(String name, String description, String instructor, String gymLocation,
                        Category category, Level level, Integer durationMinutes, Integer maxParticipants,
                        Integer currentParticipants, BigDecimal price, LocalDateTime dateTime,
                        ClassStatus status) {
        this.name = name;
        this.description = description;
        this.instructor = instructor;
        this.gymLocation = gymLocation;
        this.category = category;
        this.level = level;
        this.durationMinutes = durationMinutes;
        this.maxParticipants = maxParticipants;
        this.currentParticipants = currentParticipants;
        this.price = price;
        this.dateTime = dateTime;
        this.status = status;
    }

    /**
     * Reserve {@code spots} places. Invariant metier : currentParticipants ne depasse jamais
     * maxParticipants.
     */
    public void incrementParticipants(int spots) {
        if (currentParticipants + spots > maxParticipants) {
            throw new NoSpotsAvailableException(id, availableSpots(), spots);
        }
        this.currentParticipants += spots;
    }

    /**
     * Libere {@code spots} places (annulation d'une reservation). Le compteur ne descend jamais
     * sous zero, meme si une compensation est rejouee.
     */
    public void decrementParticipants(int spots) {
        this.currentParticipants = Math.max(0, this.currentParticipants - spots);
    }

    public int availableSpots() {
        return maxParticipants - currentParticipants;
    }

    public boolean hasAvailableSpots(int spots) {
        return currentParticipants + spots <= maxParticipants;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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

    public Integer getCurrentParticipants() {
        return currentParticipants;
    }

    public void setCurrentParticipants(Integer currentParticipants) {
        this.currentParticipants = currentParticipants;
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

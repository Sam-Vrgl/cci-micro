package com.formation.fitclass.dto;

import com.formation.fitclass.model.Category;
import com.formation.fitclass.model.ClassStatus;
import com.formation.fitclass.model.Level;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

/**
 * Filtres de recherche lies depuis la query string, partages par GET /api/classes et
 * GET /api/classes/search. Tous les champs sont optionnels : un champ nul n'ajoute aucun predicat.
 */
public class ClassSearchCriteria {

    private Category category;
    private Level level;

    /** Correspondance partielle, insensible a la casse, sur gymLocation. */
    private String location;

    /** Correspondance partielle, insensible a la casse, sur instructor. */
    private String instructor;

    private ClassStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    /** Borne incluse : un cours du 20 a 19h est retenu par dateTo=2026-09-20. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;

    public ClassSearchCriteria() {}

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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public ClassStatus getStatus() {
        return status;
    }

    public void setStatus(ClassStatus status) {
        this.status = status;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }
}

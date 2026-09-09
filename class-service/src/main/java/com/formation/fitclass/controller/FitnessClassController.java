package com.formation.fitclass.controller;

import com.formation.fitclass.dto.ClassSearchCriteria;
import com.formation.fitclass.dto.FitnessClassRequest;
import com.formation.fitclass.dto.FitnessClassResponse;
import com.formation.fitclass.service.FitnessClassService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/classes")
@Validated
public class FitnessClassController {

    private final FitnessClassService classService;

    public FitnessClassController(FitnessClassService classService) {
        this.classService = classService;
    }

    /** Liste paginee, filtrable : ?category=YOGA&level=BEGINNER&page=0&size=10&sort=dateTime,asc */
    @GetMapping
    public Page<FitnessClassResponse> getAll(
            ClassSearchCriteria criteria,
            @PageableDefault(size = 10, sort = "dateTime", direction = Sort.Direction.ASC) Pageable pageable) {
        return classService.search(criteria, pageable);
    }

    /** Meme moteur que GET /api/classes, expose sous l'URL de recherche du cahier des charges. */
    @GetMapping("/search")
    public Page<FitnessClassResponse> search(
            ClassSearchCriteria criteria,
            @PageableDefault(size = 10, sort = "dateTime", direction = Sort.Direction.ASC) Pageable pageable) {
        return classService.search(criteria, pageable);
    }

    @GetMapping("/{id}")
    public FitnessClassResponse getById(@PathVariable Long id) {
        return classService.findById(id);
    }

    @PostMapping
    public ResponseEntity<FitnessClassResponse> create(@Valid @RequestBody FitnessClassRequest request) {
        FitnessClassResponse created = classService.create(request);
        return ResponseEntity.created(URI.create("/api/classes/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public FitnessClassResponse update(@PathVariable Long id,
                                       @Valid @RequestBody FitnessClassRequest request) {
        return classService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        classService.cancel(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** Appele par booking-service : reserve des places. 409 si le cours est complet. */
    @PatchMapping("/{id}/increment")
    public FitnessClassResponse increment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "spots doit etre au moins 1") int spots) {
        return classService.incrementParticipants(id, spots);
    }

    /** Appele par booking-service : libere des places (annulation, compensation). */
    @PatchMapping("/{id}/decrement")
    public FitnessClassResponse decrement(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "spots doit etre au moins 1") int spots) {
        return classService.decrementParticipants(id, spots);
    }
}

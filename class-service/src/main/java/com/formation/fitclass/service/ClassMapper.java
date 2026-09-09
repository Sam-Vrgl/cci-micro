package com.formation.fitclass.service;

import com.formation.fitclass.dto.FitnessClassRequest;
import com.formation.fitclass.dto.FitnessClassResponse;
import com.formation.fitclass.model.ClassStatus;
import com.formation.fitclass.model.FitnessClass;

public final class ClassMapper {

    private ClassMapper() {}

    public static FitnessClass toEntity(FitnessClassRequest request) {
        return new FitnessClass(request.getName(), request.getDescription(), request.getInstructor(),
                request.getGymLocation(), request.getCategory(), request.getLevel(),
                request.getDurationMinutes(), request.getMaxParticipants(), 0, request.getPrice(),
                request.getDateTime(),
                request.getStatus() != null ? request.getStatus() : ClassStatus.SCHEDULED);
    }

    public static FitnessClassResponse toResponse(FitnessClass fitnessClass) {
        return new FitnessClassResponse(fitnessClass.getId(), fitnessClass.getName(),
                fitnessClass.getDescription(), fitnessClass.getInstructor(), fitnessClass.getGymLocation(),
                fitnessClass.getCategory(), fitnessClass.getLevel(), fitnessClass.getDurationMinutes(),
                fitnessClass.getMaxParticipants(), fitnessClass.getCurrentParticipants(),
                fitnessClass.availableSpots(), fitnessClass.getPrice(), fitnessClass.getDateTime(),
                fitnessClass.getStatus());
    }
}

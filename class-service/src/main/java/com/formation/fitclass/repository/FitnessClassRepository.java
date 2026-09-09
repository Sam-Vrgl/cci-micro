package com.formation.fitclass.repository;

import com.formation.fitclass.model.FitnessClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FitnessClassRepository
        extends JpaRepository<FitnessClass, Long>, JpaSpecificationExecutor<FitnessClass> {
}

package com.formation.fitclass.service;

import com.formation.fitclass.dto.ClassSearchCriteria;
import com.formation.fitclass.dto.FitnessClassRequest;
import com.formation.fitclass.dto.FitnessClassResponse;
import com.formation.fitclass.exception.FitnessClassNotFoundException;
import com.formation.fitclass.exception.ConcurrentUpdateException;
import com.formation.fitclass.exception.InvalidClassStateException;
import com.formation.fitclass.model.ClassStatus;
import com.formation.fitclass.model.FitnessClass;
import com.formation.fitclass.repository.FitnessClassRepository;
import com.formation.fitclass.repository.FitnessClassSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Consumer;

@Service
public class FitnessClassService {

    /**
     * Nombre de tentatives sur une collision de version. Deux reservations concurrentes sur des
     * places encore libres doivent aboutir toutes les deux : seule la saturation reelle du cours
     * remonte en 409.
     */
    private static final int MAX_CONCURRENCY_ATTEMPTS = 3;

    private final FitnessClassRepository classRepository;
    private final TransactionTemplate transactionTemplate;

    public FitnessClassService(FitnessClassRepository classRepository,
                               PlatformTransactionManager transactionManager) {
        this.classRepository = classRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public Page<FitnessClassResponse> search(ClassSearchCriteria criteria, Pageable pageable) {
        return classRepository.findAll(FitnessClassSpecifications.from(criteria), pageable)
                .map(ClassMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public FitnessClassResponse findById(Long id) {
        return ClassMapper.toResponse(getClassOrThrow(id));
    }

    @Transactional
    public FitnessClassResponse create(FitnessClassRequest request) {
        return ClassMapper.toResponse(classRepository.save(ClassMapper.toEntity(request)));
    }

    @Transactional
    public FitnessClassResponse update(Long id, FitnessClassRequest request) {
        FitnessClass fitnessClass = getClassOrThrow(id);

        if (request.getMaxParticipants() < fitnessClass.getCurrentParticipants()) {
            throw new InvalidClassStateException("Le cours " + id + " compte deja "
                    + fitnessClass.getCurrentParticipants() + " participants : la capacite ne peut pas "
                    + "etre ramenee a " + request.getMaxParticipants());
        }

        fitnessClass.setName(request.getName());
        fitnessClass.setDescription(request.getDescription());
        fitnessClass.setInstructor(request.getInstructor());
        fitnessClass.setGymLocation(request.getGymLocation());
        fitnessClass.setCategory(request.getCategory());
        fitnessClass.setLevel(request.getLevel());
        fitnessClass.setDurationMinutes(request.getDurationMinutes());
        fitnessClass.setMaxParticipants(request.getMaxParticipants());
        fitnessClass.setPrice(request.getPrice());
        fitnessClass.setDateTime(request.getDateTime());
        if (request.getStatus() != null) {
            fitnessClass.setStatus(request.getStatus());
        }

        return ClassMapper.toResponse(classRepository.save(fitnessClass));
    }

    /**
     * Annulation logique : le cours reste en base car des reservations le referencent
     * (snapshot cote booking-service, historique des paiements).
     */
    @Transactional
    public void cancel(Long id) {
        FitnessClass fitnessClass = getClassOrThrow(id);
        fitnessClass.setStatus(ClassStatus.CANCELLED);
        classRepository.save(fitnessClass);
    }

    /** Appele par booking-service lors de la creation d'une reservation. */
    public FitnessClassResponse incrementParticipants(Long id, int spots) {
        return updateParticipants(id, fitnessClass -> {
            if (fitnessClass.getStatus() != ClassStatus.SCHEDULED) {
                throw new InvalidClassStateException(id, fitnessClass.getStatus());
            }
            fitnessClass.incrementParticipants(spots);
        });
    }

    /** Appele par booking-service lors d'une annulation ou d'une compensation. */
    public FitnessClassResponse decrementParticipants(Long id, int spots) {
        return updateParticipants(id, fitnessClass -> fitnessClass.decrementParticipants(spots));
    }

    /**
     * Applique une modification du compteur de places sous verrouillage optimiste. Le
     * TransactionTemplate delimite chaque tentative : le flush explicite fait remonter la
     * collision de version ici meme, ou elle peut etre rejouee sur des donnees fraiches.
     */
    private FitnessClassResponse updateParticipants(Long id, Consumer<FitnessClass> update) {
        ObjectOptimisticLockingFailureException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_CONCURRENCY_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> {
                    FitnessClass fitnessClass = getClassOrThrow(id);
                    update.accept(fitnessClass);
                    return ClassMapper.toResponse(classRepository.saveAndFlush(fitnessClass));
                });
            } catch (ObjectOptimisticLockingFailureException ex) {
                lastFailure = ex;
            }
        }

        throw new ConcurrentUpdateException(id, lastFailure);
    }

    private FitnessClass getClassOrThrow(Long id) {
        return classRepository.findById(id)
                .orElseThrow(() -> new FitnessClassNotFoundException(id));
    }
}

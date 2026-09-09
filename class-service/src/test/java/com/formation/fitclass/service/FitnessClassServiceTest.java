package com.formation.fitclass.service;

import com.formation.fitclass.dto.FitnessClassRequest;
import com.formation.fitclass.dto.FitnessClassResponse;
import com.formation.fitclass.exception.FitnessClassNotFoundException;
import com.formation.fitclass.exception.InvalidClassStateException;
import com.formation.fitclass.exception.NoSpotsAvailableException;
import com.formation.fitclass.model.Category;
import com.formation.fitclass.model.ClassStatus;
import com.formation.fitclass.model.FitnessClass;
import com.formation.fitclass.model.Level;
import com.formation.fitclass.repository.FitnessClassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FitnessClassServiceTest {

    @Mock
    private FitnessClassRepository classRepository;

    private FitnessClassService classService;

    @BeforeEach
    void setUp() {
        // Le service delimite ses transactions lui-meme (rejeu du verrouillage optimiste) :
        // un gestionnaire simule suffit pour executer le callback en test unitaire.
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = new SimpleTransactionStatus();
        lenient().when(transactionManager.getTransaction(any())).thenReturn(status);
        classService = new FitnessClassService(classRepository, transactionManager);
    }

    private FitnessClass fitnessClass(Long id, int maxParticipants, int currentParticipants) {
        FitnessClass fitnessClass = new FitnessClass("Yoga Vinyasa", "Enchainements fluides",
                "Marie Dupont", "Paris 11e", Category.YOGA, Level.BEGINNER, 60, maxParticipants,
                currentParticipants, new BigDecimal("15.00"), LocalDateTime.now().plusDays(3),
                ClassStatus.SCHEDULED);
        fitnessClass.setId(id);
        return fitnessClass;
    }

    @Test
    void incrementParticipants_placesDisponibles_reserveLesPlaces() {
        FitnessClass fitnessClass = fitnessClass(1L, 10, 5);
        when(classRepository.findById(1L)).thenReturn(Optional.of(fitnessClass));
        when(classRepository.saveAndFlush(any(FitnessClass.class))).thenAnswer(inv -> inv.getArgument(0));

        FitnessClassResponse result = classService.incrementParticipants(1L, 2);

        assertThat(result.getCurrentParticipants()).isEqualTo(7);
        assertThat(result.getAvailableSpots()).isEqualTo(3);
    }

    @Test
    void incrementParticipants_placesInsuffisantes_leveNoSpotsAvailableException() {
        FitnessClass fitnessClass = fitnessClass(1L, 10, 9);
        when(classRepository.findById(1L)).thenReturn(Optional.of(fitnessClass));

        assertThatThrownBy(() -> classService.incrementParticipants(1L, 2))
                .isInstanceOf(NoSpotsAvailableException.class)
                .hasMessageContaining("Plus de places disponibles");

        assertThat(fitnessClass.getCurrentParticipants()).isEqualTo(9);
        verify(classRepository, never()).saveAndFlush(any(FitnessClass.class));
    }

    @Test
    void incrementParticipants_dernierePlace_atteintLaCapaciteMaximale() {
        FitnessClass fitnessClass = fitnessClass(1L, 10, 9);
        when(classRepository.findById(1L)).thenReturn(Optional.of(fitnessClass));
        when(classRepository.saveAndFlush(any(FitnessClass.class))).thenAnswer(inv -> inv.getArgument(0));

        FitnessClassResponse result = classService.incrementParticipants(1L, 1);

        assertThat(result.getCurrentParticipants()).isEqualTo(10);
        assertThat(result.getAvailableSpots()).isZero();
    }

    @Test
    void incrementParticipants_coursAnnule_leveInvalidClassStateException() {
        FitnessClass fitnessClass = fitnessClass(1L, 10, 0);
        fitnessClass.setStatus(ClassStatus.CANCELLED);
        when(classRepository.findById(1L)).thenReturn(Optional.of(fitnessClass));

        assertThatThrownBy(() -> classService.incrementParticipants(1L, 1))
                .isInstanceOf(InvalidClassStateException.class);
    }

    @Test
    void decrementParticipants_libereLesPlacesSansPasserSousZero() {
        FitnessClass fitnessClass = fitnessClass(1L, 10, 2);
        when(classRepository.findById(1L)).thenReturn(Optional.of(fitnessClass));
        when(classRepository.saveAndFlush(any(FitnessClass.class))).thenAnswer(inv -> inv.getArgument(0));

        FitnessClassResponse result = classService.decrementParticipants(1L, 5);

        assertThat(result.getCurrentParticipants()).isZero();
    }

    @Test
    void findById_coursInconnu_leveFitnessClassNotFoundException() {
        when(classRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classService.findById(99L))
                .isInstanceOf(FitnessClassNotFoundException.class);
    }

    @Test
    void create_initialiseLeCoursAZeroParticipantEtEnStatutScheduled() {
        when(classRepository.save(any(FitnessClass.class))).thenAnswer(inv -> {
            FitnessClass saved = inv.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        FitnessClassRequest request = new FitnessClassRequest("Zumba Party", "Cardio danse", "Ana Silva",
                "Lyon 3e", Category.ZUMBA, Level.BEGINNER, 60, 30, new BigDecimal("12.00"),
                LocalDateTime.now().plusDays(5), null);

        FitnessClassResponse result = classService.create(request);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getCurrentParticipants()).isZero();
        assertThat(result.getStatus()).isEqualTo(ClassStatus.SCHEDULED);
    }

    @Test
    void update_capaciteInferieureAuxInscrits_leveInvalidClassStateException() {
        when(classRepository.findById(1L)).thenReturn(Optional.of(fitnessClass(1L, 20, 12)));

        FitnessClassRequest request = new FitnessClassRequest("Yoga Vinyasa", "Enchainements fluides",
                "Marie Dupont", "Paris 11e", Category.YOGA, Level.BEGINNER, 60, 10,
                new BigDecimal("15.00"), LocalDateTime.now().plusDays(3), null);

        assertThatThrownBy(() -> classService.update(1L, request))
                .isInstanceOf(InvalidClassStateException.class)
                .hasMessageContaining("compte deja 12 participants");
    }

    @Test
    void cancel_passeLeCoursEnStatutCancelled() {
        FitnessClass fitnessClass = fitnessClass(1L, 10, 3);
        when(classRepository.findById(1L)).thenReturn(Optional.of(fitnessClass));

        classService.cancel(1L);

        assertThat(fitnessClass.getStatus()).isEqualTo(ClassStatus.CANCELLED);
        verify(classRepository).save(eq(fitnessClass));
    }
}

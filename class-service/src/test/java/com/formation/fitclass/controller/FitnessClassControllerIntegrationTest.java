package com.formation.fitclass.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.formation.fitclass.dto.FitnessClassRequest;
import com.formation.fitclass.model.Category;
import com.formation.fitclass.model.ClassStatus;
import com.formation.fitclass.model.FitnessClass;
import com.formation.fitclass.model.Level;
import com.formation.fitclass.repository.FitnessClassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FitnessClassControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private FitnessClassRepository classRepository;

    @BeforeEach
    void cleanDatabase() {
        classRepository.deleteAll();
    }

    private FitnessClass persistClass(String name, String instructor, String location, Category category,
                                      Level level, int maxParticipants, int currentParticipants,
                                      LocalDateTime dateTime) {
        return classRepository.save(new FitnessClass(name, "Description de " + name, instructor, location,
                category, level, 60, maxParticipants, currentParticipants, new BigDecimal("15.00"),
                dateTime, ClassStatus.SCHEDULED));
    }

    private FitnessClassRequest validRequest() {
        return new FitnessClassRequest("Yoga Vinyasa", "Enchainements fluides", "Marie Dupont",
                "Paris 11e", Category.YOGA, Level.BEGINNER, 60, 20, new BigDecimal("15.00"),
                LocalDateTime.now().plusDays(3), null);
    }

    @Test
    void create_requeteValide_retourne201EtInitialiseLeCompteur() throws Exception {
        mockMvc.perform(post("/api/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Yoga Vinyasa"))
                .andExpect(jsonPath("$.currentParticipants").value(0))
                .andExpect(jsonPath("$.availableSpots").value(20))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void create_nomTropCourt_retourne400() throws Exception {
        FitnessClassRequest request = validRequest();
        request.setName("Yo");

        mockMvc.perform(post("/api/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void create_dureeNonAutorisee_retourne400() throws Exception {
        FitnessClassRequest request = validRequest();
        request.setDurationMinutes(50);

        mockMvc.perform(post("/api/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").exists());
    }

    @Test
    void create_dateDansLePasse_retourne400() throws Exception {
        FitnessClassRequest request = validRequest();
        request.setDateTime(LocalDateTime.now().minusDays(1));

        mockMvc.perform(post("/api/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateTime").exists());
    }

    @Test
    void create_prixInferieurAuMinimum_retourne400() throws Exception {
        FitnessClassRequest request = validRequest();
        request.setPrice(new BigDecimal("4.99"));

        mockMvc.perform(post("/api/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }

    @Test
    void getById_coursInconnu_retourne404() throws Exception {
        mockMvc.perform(get("/api/classes/{id}", 9999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getAll_retourneUnePageTrieeParDate() throws Exception {
        persistClass("Cours tardif", "Marie Dupont", "Paris 11e", Category.YOGA, Level.BEGINNER,
                20, 0, LocalDateTime.now().plusDays(9));
        persistClass("Cours proche", "Marie Dupont", "Paris 11e", Category.YOGA, Level.BEGINNER,
                20, 0, LocalDateTime.now().plusDays(1));

        mockMvc.perform(get("/api/classes").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Cours proche"))
                .andExpect(jsonPath("$.content[1].name").value("Cours tardif"));
    }

    @Test
    void search_filtreParCategorieEtNiveau() throws Exception {
        persistClass("Yoga debutant", "Marie Dupont", "Paris 11e", Category.YOGA, Level.BEGINNER,
                20, 0, LocalDateTime.now().plusDays(2));
        persistClass("CrossFit avance", "Karim Benali", "Paris 15e", Category.CROSSFIT, Level.ADVANCED,
                12, 0, LocalDateTime.now().plusDays(3));

        mockMvc.perform(get("/api/classes/search")
                        .param("category", "YOGA")
                        .param("level", "BEGINNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Yoga debutant"));
    }

    @Test
    void search_filtreParLieuEtInstructeurInsensibleALaCasse() throws Exception {
        persistClass("Yoga Lyon", "Ana Silva", "Lyon 3e", Category.YOGA, Level.BEGINNER,
                20, 0, LocalDateTime.now().plusDays(2));
        persistClass("Yoga Paris", "Marie Dupont", "Paris 11e", Category.YOGA, Level.BEGINNER,
                20, 0, LocalDateTime.now().plusDays(2));

        mockMvc.perform(get("/api/classes/search")
                        .param("location", "lyon")
                        .param("instructor", "ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Yoga Lyon"));
    }

    @Test
    void search_filtreParFenetreDeDates() throws Exception {
        LocalDateTime inWindow = LocalDateTime.now().plusDays(2).withHour(19).withMinute(0);
        persistClass("Dans la fenetre", "Marie Dupont", "Paris 11e", Category.YOGA, Level.BEGINNER,
                20, 0, inWindow);
        persistClass("Hors fenetre", "Marie Dupont", "Paris 11e", Category.YOGA, Level.BEGINNER,
                20, 0, LocalDateTime.now().plusDays(20));

        mockMvc.perform(get("/api/classes/search")
                        .param("dateFrom", inWindow.toLocalDate().toString())
                        .param("dateTo", inWindow.toLocalDate().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Dans la fenetre"));
    }

    @Test
    void increment_placesDisponibles_retourne200EtIncrementeLeCompteur() throws Exception {
        FitnessClass saved = persistClass("Yoga Vinyasa", "Marie Dupont", "Paris 11e", Category.YOGA,
                Level.BEGINNER, 10, 5, LocalDateTime.now().plusDays(3));

        mockMvc.perform(patch("/api/classes/{id}/increment", saved.getId()).param("spots", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentParticipants").value(7))
                .andExpect(jsonPath("$.availableSpots").value(3));

        assertThat(classRepository.findById(saved.getId()).orElseThrow().getCurrentParticipants())
                .isEqualTo(7);
    }

    @Test
    void increment_placesInsuffisantes_retourne409EtNeModifieRien() throws Exception {
        FitnessClass saved = persistClass("Boxing Sparring", "Karim Benali", "Paris 15e", Category.BOXING,
                Level.ADVANCED, 10, 9, LocalDateTime.now().plusDays(2));

        mockMvc.perform(patch("/api/classes/{id}/increment", saved.getId()).param("spots", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "Plus de places disponibles")));

        assertThat(classRepository.findById(saved.getId()).orElseThrow().getCurrentParticipants())
                .isEqualTo(9);
    }

    @Test
    void decrement_libereLesPlaces() throws Exception {
        FitnessClass saved = persistClass("Yoga Vinyasa", "Marie Dupont", "Paris 11e", Category.YOGA,
                Level.BEGINNER, 10, 5, LocalDateTime.now().plusDays(3));

        mockMvc.perform(patch("/api/classes/{id}/decrement", saved.getId()).param("spots", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentParticipants").value(3));
    }

    @Test
    void delete_annuleLeCoursSansLeSupprimer() throws Exception {
        FitnessClass saved = persistClass("Yoga Vinyasa", "Marie Dupont", "Paris 11e", Category.YOGA,
                Level.BEGINNER, 10, 0, LocalDateTime.now().plusDays(3));

        mockMvc.perform(delete("/api/classes/{id}", saved.getId()))
                .andExpect(status().isNoContent());

        assertThat(classRepository.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(ClassStatus.CANCELLED);
    }

    @Test
    void increment_coursAnnule_retourne409() throws Exception {
        FitnessClass saved = persistClass("Yoga Vinyasa", "Marie Dupont", "Paris 11e", Category.YOGA,
                Level.BEGINNER, 10, 0, LocalDateTime.now().plusDays(3));
        saved.setStatus(ClassStatus.CANCELLED);
        classRepository.save(saved);

        mockMvc.perform(patch("/api/classes/{id}/increment", saved.getId()).param("spots", "1"))
                .andExpect(status().isConflict());
    }
}

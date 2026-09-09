package com.formation.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.formation.booking.client.ClassClient;
import com.formation.booking.client.NotificationClient;
import com.formation.booking.client.PaymentClient;
import com.formation.booking.dto.BookingRequest;
import com.formation.booking.dto.ConfirmPaymentRequest;
import com.formation.booking.dto.FitnessClassDto;
import com.formation.booking.dto.NotificationRequestDto;
import com.formation.booking.dto.PaymentRequestDto;
import com.formation.booking.dto.PaymentResponseDto;
import com.formation.booking.model.Booking;
import com.formation.booking.model.BookingStatus;
import com.formation.booking.repository.BookingRepository;
import com.formation.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingService bookingService;

    // Les trois services distants sont simules : le test verifie l'orchestration, pas le reseau.
    @MockBean
    private ClassClient classClient;
    @MockBean
    private PaymentClient paymentClient;
    @MockBean
    private NotificationClient notificationClient;

    @BeforeEach
    void cleanDatabase() {
        bookingRepository.deleteAll();
    }

    private FitnessClassDto fitnessClass(int maxParticipants, int currentParticipants,
                                         LocalDateTime dateTime) {
        FitnessClassDto dto = new FitnessClassDto();
        dto.setId(101L);
        dto.setName("Yoga Vinyasa");
        dto.setInstructor("Marie Dupont");
        dto.setPrice(new BigDecimal("15.00"));
        dto.setDateTime(dateTime);
        dto.setMaxParticipants(maxParticipants);
        dto.setCurrentParticipants(currentParticipants);
        dto.setAvailableSpots(maxParticipants - currentParticipants);
        dto.setStatus("SCHEDULED");
        return dto;
    }

    private PaymentResponseDto payment(Long id, String status) {
        PaymentResponseDto payment = new PaymentResponseDto();
        payment.setId(id);
        payment.setPaymentReference("PAY-XY12Z");
        payment.setBookingId(10L);
        payment.setAmount(new BigDecimal("30.00"));
        payment.setStatus(status);
        if (!"SUCCESS".equals(status)) {
            payment.setFailureReason("Montant superieur ou egal a 100.00 EUR");
        }
        return payment;
    }

    private BookingRequest request(int spots) {
        return new BookingRequest(1L, "john@example.com", "John Doe", 101L, spots);
    }

    private Booking persistBooking(BookingStatus status, LocalDateTime classDate,
                                   LocalDateTime paymentDeadline) {
        return bookingRepository.save(new Booking("BK-" + System.nanoTime() % 100000, 1L,
                "john@example.com", "John Doe", 101L, "Yoga Vinyasa", classDate, "Marie Dupont",
                new BigDecimal("15.00"), 2, new BigDecimal("30.00"), LocalDateTime.now(), status,
                paymentDeadline, classDate.minusHours(Booking.CANCELLATION_WINDOW_HOURS)));
    }

    private Long createBookingViaApi(int spots) throws Exception {
        String body = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(spots))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    /** Parcours complet impose par le cahier des charges : creation, paiement, confirmation. */
    @Test
    void shouldCompleteFullBookingFlow() throws Exception {
        // 1. Le cours existe et a des places
        when(classClient.getById(101L)).thenReturn(fitnessClass(10, 5, LocalDateTime.now().plusDays(5)));
        when(paymentClient.process(any(PaymentRequestDto.class))).thenReturn(payment(7L, "SUCCESS"));

        // 2. Creation de la reservation
        Long bookingId = createBookingViaApi(2);

        mockMvc.perform(get("/api/bookings/{id}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(30.00));

        // 3. Confirmation du paiement
        mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConfirmPaymentRequest("CREDIT_CARD", "1234", "txn_123456"))))
                .andExpect(status().isOk())
                // 4. La reservation est confirmee
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);

        // 5. Les places ont bien ete prises dans class-service
        verify(classClient).increment(101L, 2);

        // 6. Les deux notifications ont ete emises
        ArgumentCaptor<NotificationRequestDto> captor =
                ArgumentCaptor.forClass(NotificationRequestDto.class);
        verify(notificationClient, org.mockito.Mockito.times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(NotificationRequestDto::getType)
                .containsExactly("BOOKING_CONFIRMATION", "PAYMENT_CONFIRMATION");
    }

    /** Le scheduler annule les reservations impayees et rend leurs places. */
    @Test
    void shouldCancelExpiredBookings() {
        // 1. Une reservation dont le delai de paiement est deja passe
        Booking expired = persistBooking(BookingStatus.PENDING_PAYMENT,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().minusHours(2));
        Booking stillValid = persistBooking(BookingStatus.PENDING_PAYMENT,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusMinutes(30));

        // 2. Passage du scheduler
        int cancelled = bookingService.cancelExpiredBookings();

        // 3. Seule la reservation expiree est annulee
        assertThat(cancelled).isEqualTo(1);
        assertThat(bookingRepository.findById(expired.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(stillValid.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);

        // 4. Les places sont restituees
        verify(classClient).decrement(101L, 2);
        verify(notificationClient).send(any(NotificationRequestDto.class));
    }

    @Test
    void create_placesInsuffisantes_retourne409() throws Exception {
        when(classClient.getById(101L)).thenReturn(fitnessClass(10, 9, LocalDateTime.now().plusDays(5)));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(bookingRepository.count()).isZero();
        verify(classClient, never()).increment(anyLong(), anyInt());
    }

    @Test
    void create_nombreDePlacesHorsBornes_retourne400() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.numberOfSpots").exists());
    }

    @Test
    void create_emailInvalide_retourne400() throws Exception {
        BookingRequest request = request(2);
        request.setUserEmail("pas-une-adresse");

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.userEmail").exists());
    }

    @Test
    void confirm_paiementRefuse_retourne402EtLaisseLaReservationPayable() throws Exception {
        when(classClient.getById(101L)).thenReturn(fitnessClass(10, 0, LocalDateTime.now().plusDays(5)));
        when(paymentClient.process(any(PaymentRequestDto.class))).thenReturn(payment(7L, "FAILED"));

        Long bookingId = createBookingViaApi(2);

        mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConfirmPaymentRequest("CREDIT_CARD", "1234", null))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402));

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void confirm_paiementExpire_retourne409() throws Exception {
        Booking expired = persistBooking(BookingStatus.PENDING_PAYMENT,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().minusMinutes(1));

        mockMvc.perform(patch("/api/bookings/{id}/confirm", expired.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConfirmPaymentRequest("CREDIT_CARD", "1234", null))))
                .andExpect(status().isConflict());

        verify(paymentClient, never()).process(any(PaymentRequestDto.class));
    }

    @Test
    void cancel_dansLesDelais_rembourseEtLibereLesPlaces() throws Exception {
        Booking confirmed = persistBooking(BookingStatus.CONFIRMED, LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusMinutes(30));
        when(paymentClient.getByBookingId(confirmed.getId())).thenReturn(payment(7L, "SUCCESS"));
        when(paymentClient.refund(7L)).thenReturn(payment(7L, "REFUNDED"));

        mockMvc.perform(patch("/api/bookings/{id}/cancel", confirmed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationDate").exists());

        verify(paymentClient).refund(7L);
        verify(classClient).decrement(101L, 2);
    }

    @Test
    void cancel_horsDelai_retourne409() throws Exception {
        // Cours dans 12h : la limite d'annulation est deja passee
        Booking confirmed = persistBooking(BookingStatus.CONFIRMED, LocalDateTime.now().plusHours(12),
                LocalDateTime.now().plusMinutes(30));

        mockMvc.perform(patch("/api/bookings/{id}/cancel", confirmed.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Annulation non autorisee")));

        verify(classClient, never()).decrement(anyLong(), anyInt());
    }

    @Test
    void getExpired_listeLesReservationsImpayeesEchues() throws Exception {
        persistBooking(BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusDays(5),
                LocalDateTime.now().minusHours(2));
        persistBooking(BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusMinutes(30));

        mockMvc.perform(get("/api/bookings/expired"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getByUserId_retourneLesReservationsDeLUtilisateur() throws Exception {
        persistBooking(BookingStatus.CONFIRMED, LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusMinutes(30));

        mockMvc.perform(get("/api/bookings/user/{userId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(1));
    }

    @Test
    void getById_reservationInconnue_retourne404() throws Exception {
        mockMvc.perform(get("/api/bookings/{id}", 9999))
                .andExpect(status().isNotFound());
    }

    @Test
    void complete_reservationConfirmee_retourne200() throws Exception {
        Booking confirmed = persistBooking(BookingStatus.CONFIRMED, LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(2));

        mockMvc.perform(patch("/api/bookings/{id}/complete", confirmed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void sendClassReminders_envoieUnSeulRappelParReservation() {
        persistBooking(BookingStatus.CONFIRMED, LocalDateTime.now().plusHours(20),
                LocalDateTime.now().minusHours(1));

        assertThat(bookingService.sendClassReminders()).isEqualTo(1);
        // Deuxieme passage : le drapeau reminderSent empeche le doublon
        assertThat(bookingService.sendClassReminders()).isZero();

        verify(notificationClient, org.mockito.Mockito.times(1))
                .send(any(NotificationRequestDto.class));
    }

    @Test
    void getAll_retourneToutesLesReservations() throws Exception {
        persistBooking(BookingStatus.CONFIRMED, LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusMinutes(30));
        persistBooking(BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusDays(6),
                LocalDateTime.now().plusMinutes(30));

        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void create_classServiceIndisponible_retourne503() throws Exception {
        when(classClient.getById(eq(101L)))
                .thenThrow(new com.formation.booking.exception.RemoteServiceUnavailableException(
                        "class-service", new IllegalStateException("connexion refusee")));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(2))))
                .andExpect(status().isServiceUnavailable());

        assertThat(bookingRepository.findAll()).isEmpty();
    }

    @Test
    void cancel_notificationIndisponible_nEmpechePasLAnnulation() throws Exception {
        Booking pending = persistBooking(BookingStatus.PENDING_PAYMENT,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusMinutes(30));
        org.mockito.Mockito.doThrow(new IllegalStateException("notification-service KO"))
                .when(notificationClient).send(any(NotificationRequestDto.class));

        mockMvc.perform(patch("/api/bookings/{id}/cancel", pending.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(bookingRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void findAll_baseVide_retourneUneListeVide() throws Exception {
        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void persistBooking_conserveLInstantaneDuCours() {
        Booking booking = persistBooking(BookingStatus.CONFIRMED, LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusMinutes(30));

        List<Booking> all = bookingRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getClassName()).isEqualTo("Yoga Vinyasa");
        assertThat(all.get(0).getInstructor()).isEqualTo("Marie Dupont");
        // H2 stocke les timestamps a la microseconde : la comparaison se fait a la seconde
        assertThat(all.get(0).getCancellationDeadline().truncatedTo(ChronoUnit.SECONDS))
                .isEqualTo(booking.getClassDate().minusHours(24).truncatedTo(ChronoUnit.SECONDS));
    }
}

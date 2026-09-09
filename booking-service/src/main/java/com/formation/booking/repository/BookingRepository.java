package com.formation.booking.repository;

import com.formation.booking.model.Booking;
import com.formation.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserIdOrderByBookingDateDesc(Long userId);

    /** Reservations impayees dont le delai est ecoule : alimente le scheduler et /api/bookings/expired. */
    List<Booking> findByStatusAndPaymentDeadlineBefore(BookingStatus status, LocalDateTime deadline);

    /** Cours a rappeler : confirmes, dans la fenetre visee, et pas encore rappeles. */
    List<Booking> findByStatusAndReminderSentFalseAndClassDateBetween(
            BookingStatus status, LocalDateTime from, LocalDateTime to);

    boolean existsByBookingReference(String bookingReference);
}

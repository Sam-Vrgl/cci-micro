package com.formation.booking.service;

import com.formation.booking.dto.BookingResponse;
import com.formation.booking.model.Booking;

public final class BookingMapper {

    private BookingMapper() {}

    public static BookingResponse toResponse(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getBookingReference(), booking.getUserId(),
                booking.getUserEmail(), booking.getUserName(), booking.getClassId(),
                booking.getClassName(), booking.getClassDate(), booking.getInstructor(),
                booking.getPrice(), booking.getNumberOfSpots(), booking.getTotalAmount(),
                booking.getBookingDate(), booking.getStatus(), booking.getPaymentDeadline(),
                booking.getCancellationDeadline(), booking.getCancellationDate());
    }
}

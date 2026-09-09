package com.formation.booking.controller;

import com.formation.booking.dto.BookingRequest;
import com.formation.booking.dto.BookingResponse;
import com.formation.booking.dto.ConfirmPaymentRequest;
import com.formation.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public List<BookingResponse> getAll() {
        return bookingService.findAll();
    }

    /** Declare avant /{id} : sans cela, "expired" serait interprete comme un identifiant. */
    @GetMapping("/expired")
    public List<BookingResponse> getExpired() {
        return bookingService.findExpired();
    }

    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable Long id) {
        return bookingService.findById(id);
    }

    @GetMapping("/user/{userId}")
    public List<BookingResponse> getByUserId(@PathVariable Long userId) {
        return bookingService.findByUserId(userId);
    }

    /** Cas 1 : reservation des places, la reservation nait en PENDING_PAYMENT. */
    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        BookingResponse created = bookingService.create(request);
        return ResponseEntity.created(URI.create("/api/bookings/" + created.getId())).body(created);
    }

    /** Cas 3 : paiement puis confirmation. */
    @PatchMapping("/{id}/confirm")
    public BookingResponse confirm(@PathVariable Long id,
                                   @Valid @RequestBody ConfirmPaymentRequest request) {
        return bookingService.confirm(id, request);
    }

    /** Cas 4 : annulation dans les delais, avec remboursement eventuel. */
    @PatchMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id) {
        return bookingService.cancel(id);
    }

    @PatchMapping("/{id}/complete")
    public BookingResponse complete(@PathVariable Long id) {
        return bookingService.complete(id);
    }
}

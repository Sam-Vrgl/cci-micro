package com.formation.payment.controller;

import com.formation.payment.dto.PaymentRequest;
import com.formation.payment.dto.PaymentResponse;
import com.formation.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Appele par booking-service. Repond 201 que le paiement soit accepte ou refuse : le refus
     * est porte par le champ status (FAILED), pas par le code HTTP.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> process(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse processed = paymentService.process(request);
        return ResponseEntity.created(URI.create("/api/payments/" + processed.getId())).body(processed);
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable Long id) {
        return paymentService.findById(id);
    }

    @GetMapping("/booking/{bookingId}")
    public PaymentResponse getByBookingId(@PathVariable Long bookingId) {
        return paymentService.findByBookingId(bookingId);
    }

    @GetMapping("/user/{userId}")
    public List<PaymentResponse> getByUserId(@PathVariable Long userId) {
        return paymentService.findByUserId(userId);
    }

    /** Rembourse un paiement encaisse (annulation dans les delais cote booking-service). */
    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable Long id) {
        return paymentService.refund(id);
    }
}

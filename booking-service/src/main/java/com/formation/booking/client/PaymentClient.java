package com.formation.booking.client;

import com.formation.booking.dto.PaymentRequestDto;
import com.formation.booking.dto.PaymentResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", fallbackFactory = PaymentClientFallbackFactory.class)
public interface PaymentClient {

    @PostMapping("/api/payments")
    PaymentResponseDto process(@RequestBody PaymentRequestDto request);

    @GetMapping("/api/payments/booking/{bookingId}")
    PaymentResponseDto getByBookingId(@PathVariable("bookingId") Long bookingId);

    @PostMapping("/api/payments/{id}/refund")
    PaymentResponseDto refund(@PathVariable("id") Long id);
}

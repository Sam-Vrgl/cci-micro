package com.formation.payment.repository;

import com.formation.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Une reservation peut avoir plusieurs tentatives : on expose la plus recente. */
    Optional<Payment> findFirstByBookingIdOrderByPaymentDateDescIdDesc(Long bookingId);

    List<Payment> findByBookingIdOrderByPaymentDateDesc(Long bookingId);

    List<Payment> findByUserIdOrderByPaymentDateDesc(Long userId);

    boolean existsByPaymentReference(String paymentReference);
}

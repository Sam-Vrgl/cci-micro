package com.formation.payment.service;

import com.formation.payment.dto.PaymentResponse;
import com.formation.payment.model.Payment;
import com.formation.payment.model.PaymentStatus;

public final class PaymentMapper {

    private PaymentMapper() {}

    public static PaymentResponse toResponse(Payment payment) {
        return toResponse(payment, null);
    }

    public static PaymentResponse toResponse(Payment payment, String failureReason) {
        return new PaymentResponse(payment.getId(), payment.getPaymentReference(), payment.getBookingId(),
                payment.getBookingReference(), payment.getUserId(), payment.getAmount(),
                payment.getPaymentMethod(), payment.getCardLastFour(), payment.getTransactionId(),
                payment.getPaymentDate(), payment.getStatus(), payment.getRefundDate(),
                payment.getStatus() == PaymentStatus.FAILED ? failureReason : null);
    }
}

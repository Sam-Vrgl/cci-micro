package com.formation.payment.dto;

import com.formation.payment.model.PaymentMethod;
import com.formation.payment.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponse {

    private Long id;
    private String paymentReference;
    private Long bookingId;
    private String bookingReference;
    private Long userId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String cardLastFour;
    private String transactionId;
    private LocalDateTime paymentDate;
    private PaymentStatus status;
    private LocalDateTime refundDate;

    /** Motif du refus, renseigne uniquement lorsque status vaut FAILED. */
    private String failureReason;

    public PaymentResponse() {}

    public PaymentResponse(Long id, String paymentReference, Long bookingId, String bookingReference,
                           Long userId, BigDecimal amount, PaymentMethod paymentMethod,
                           String cardLastFour, String transactionId, LocalDateTime paymentDate,
                           PaymentStatus status, LocalDateTime refundDate, String failureReason) {
        this.id = id;
        this.paymentReference = paymentReference;
        this.bookingId = bookingId;
        this.bookingReference = bookingReference;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.cardLastFour = cardLastFour;
        this.transactionId = transactionId;
        this.paymentDate = paymentDate;
        this.status = status;
        this.refundDate = refundDate;
        this.failureReason = failureReason;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getCardLastFour() {
        return cardLastFour;
    }

    public void setCardLastFour(String cardLastFour) {
        this.cardLastFour = cardLastFour;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDateTime paymentDate) {
        this.paymentDate = paymentDate;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public LocalDateTime getRefundDate() {
        return refundDate;
    }

    public void setRefundDate(LocalDateTime refundDate) {
        this.refundDate = refundDate;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}

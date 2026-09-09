package com.formation.booking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Reponse de payment-service. Le refus est porte par status, pas par le code HTTP. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentResponseDto {

    public static final String STATUS_SUCCESS = "SUCCESS";

    private Long id;
    private String paymentReference;
    private Long bookingId;
    private BigDecimal amount;
    private String transactionId;
    private String status;
    private String failureReason;

    public PaymentResponseDto() {}

    public boolean isSuccessful() {
        return STATUS_SUCCESS.equals(status);
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}

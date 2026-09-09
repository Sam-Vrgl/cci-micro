package com.formation.booking.dto;

import com.formation.booking.model.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BookingResponse {

    private Long id;
    private String bookingReference;
    private Long userId;
    private String userEmail;
    private String userName;
    private Long classId;
    private String className;
    private LocalDateTime classDate;
    private String instructor;
    private BigDecimal price;
    private Integer numberOfSpots;
    private BigDecimal totalAmount;
    private LocalDateTime bookingDate;
    private BookingStatus status;
    private LocalDateTime paymentDeadline;
    private LocalDateTime cancellationDeadline;
    private LocalDateTime cancellationDate;

    public BookingResponse() {}

    public BookingResponse(Long id, String bookingReference, Long userId, String userEmail,
                           String userName, Long classId, String className, LocalDateTime classDate,
                           String instructor, BigDecimal price, Integer numberOfSpots,
                           BigDecimal totalAmount, LocalDateTime bookingDate, BookingStatus status,
                           LocalDateTime paymentDeadline, LocalDateTime cancellationDeadline,
                           LocalDateTime cancellationDate) {
        this.id = id;
        this.bookingReference = bookingReference;
        this.userId = userId;
        this.userEmail = userEmail;
        this.userName = userName;
        this.classId = classId;
        this.className = className;
        this.classDate = classDate;
        this.instructor = instructor;
        this.price = price;
        this.numberOfSpots = numberOfSpots;
        this.totalAmount = totalAmount;
        this.bookingDate = bookingDate;
        this.status = status;
        this.paymentDeadline = paymentDeadline;
        this.cancellationDeadline = cancellationDeadline;
        this.cancellationDate = cancellationDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public LocalDateTime getClassDate() {
        return classDate;
    }

    public void setClassDate(LocalDateTime classDate) {
        this.classDate = classDate;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getNumberOfSpots() {
        return numberOfSpots;
    }

    public void setNumberOfSpots(Integer numberOfSpots) {
        this.numberOfSpots = numberOfSpots;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDateTime bookingDate) {
        this.bookingDate = bookingDate;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public LocalDateTime getPaymentDeadline() {
        return paymentDeadline;
    }

    public void setPaymentDeadline(LocalDateTime paymentDeadline) {
        this.paymentDeadline = paymentDeadline;
    }

    public LocalDateTime getCancellationDeadline() {
        return cancellationDeadline;
    }

    public void setCancellationDeadline(LocalDateTime cancellationDeadline) {
        this.cancellationDeadline = cancellationDeadline;
    }

    public LocalDateTime getCancellationDate() {
        return cancellationDate;
    }

    public void setCancellationDate(LocalDateTime cancellationDate) {
        this.cancellationDate = cancellationDate;
    }
}

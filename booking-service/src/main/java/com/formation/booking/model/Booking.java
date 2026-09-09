package com.formation.booking.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    /** Une reservation est payable pendant une heure apres sa creation. */
    public static final int PAYMENT_WINDOW_HOURS = 1;

    /** Annulation gratuite jusqu'a 24h avant le cours. */
    public static final int CANCELLATION_WINDOW_HOURS = 24;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String bookingReference;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String userName;

    @Column(nullable = false)
    private Long classId;

    // Instantane du cours au moment de la reservation : la reservation reste lisible
    // meme si le cours est modifie ou annule par la suite.
    private String className;

    private LocalDateTime classDate;

    private String instructor;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer numberOfSpots;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private LocalDateTime bookingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private LocalDateTime paymentDeadline;

    private LocalDateTime cancellationDeadline;

    private LocalDateTime cancellationDate;

    /** Evite qu'un rappel soit envoye plusieurs fois par le scheduler horaire. */
    @Column(nullable = false)
    private Boolean reminderSent;

    public Booking() {}

    public Booking(String bookingReference, Long userId, String userEmail, String userName, Long classId,
                   String className, LocalDateTime classDate, String instructor, BigDecimal price,
                   Integer numberOfSpots, BigDecimal totalAmount, LocalDateTime bookingDate,
                   BookingStatus status, LocalDateTime paymentDeadline,
                   LocalDateTime cancellationDeadline) {
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
        this.reminderSent = false;
    }

    public boolean isPaymentWindowClosed(LocalDateTime now) {
        return paymentDeadline != null && paymentDeadline.isBefore(now);
    }

    public boolean isCancellationWindowClosed(LocalDateTime now) {
        return cancellationDeadline != null && cancellationDeadline.isBefore(now);
    }

    /** Une reservation terminee ou deja annulee ne peut plus changer d'etat. */
    public boolean isClosed() {
        return status == BookingStatus.CANCELLED || status == BookingStatus.COMPLETED;
    }

    /** Les places sont prises des la creation : elles restent a liberer tant que la reservation est active. */
    public boolean holdsSpots() {
        return status == BookingStatus.PENDING_PAYMENT || status == BookingStatus.CONFIRMED;
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

    public Boolean getReminderSent() {
        return reminderSent;
    }

    public void setReminderSent(Boolean reminderSent) {
        this.reminderSent = reminderSent;
    }
}

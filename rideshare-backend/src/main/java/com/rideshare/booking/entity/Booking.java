package com.rideshare.booking.entity;

import com.rideshare.common.entity.BaseEntity;
import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.exception.ConflictException;
import com.rideshare.ride.entity.Ride;
import com.rideshare.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "bookings")
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_id", nullable = false)
    private User passenger;

    @Column(name = "number_of_seats", nullable = false)
    private Integer numberOfSeats;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false, length = 20)
    private BookingStatus bookingStatus = BookingStatus.PENDING;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 20)
    private String bookingReference;

    /** The only way to change status; enforces the transition rules in {@link BookingStatus}. */
    public void transitionTo(BookingStatus target) {
        if (!bookingStatus.canTransitionTo(target)) {
            throw new ConflictException("Booking cannot move from " + bookingStatus + " to " + target);
        }
        this.bookingStatus = target;
    }

    public boolean isOwnedBy(java.util.UUID userId) {
        return passenger.getId().equals(userId);
    }
}

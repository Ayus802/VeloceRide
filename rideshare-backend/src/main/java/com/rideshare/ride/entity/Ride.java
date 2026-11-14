package com.rideshare.ride.entity;

import com.rideshare.common.entity.BaseEntity;
import com.rideshare.common.enums.RideStatus;
import com.rideshare.user.entity.User;
import com.rideshare.vehicle.entity.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rides")
public class Ride extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "source", nullable = false, length = 200)
    private String source;

    @Column(name = "destination", nullable = false, length = 200)
    private String destination;

    @Column(name = "source_latitude")
    private Double sourceLatitude;

    @Column(name = "source_longitude")
    private Double sourceLongitude;

    @Column(name = "destination_latitude")
    private Double destinationLatitude;

    @Column(name = "destination_longitude")
    private Double destinationLongitude;

    /** Departure date/time are local to the platform time zone ({@code app.time-zone}). */
    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;

    @Column(name = "estimated_arrival_time")
    private LocalDateTime estimatedArrivalTime;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    /** totalSeats minus seats held by ACCEPTED bookings. Only modified under a pessimistic row lock. */
    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats;

    @Column(name = "price_per_seat", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerSeat;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "ride_status", nullable = false, length = 20)
    private RideStatus rideStatus = RideStatus.SCHEDULED;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public LocalDateTime departureDateTime() {
        return LocalDateTime.of(departureDate, departureTime);
    }

    public int bookedSeats() {
        return totalSeats - availableSeats;
    }

    public String route() {
        return source + " -> " + destination;
    }

    public boolean isDrivenBy(java.util.UUID userId) {
        return driver.getId().equals(userId);
    }
}

package com.rideshare.ride.repository;

import com.rideshare.common.enums.RideStatus;
import com.rideshare.ride.entity.Ride;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

public interface RideRepository extends JpaRepository<Ride, UUID> {

    /**
     * SELECT ... FOR UPDATE on the ride row. Every seat-changing operation takes this lock first,
     * which serialises concurrent accept/cancel operations and makes overbooking impossible.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Ride r where r.id = :id")
    Optional<Ride> findByIdForUpdate(@Param("id") UUID id);

    @Query("select r from Ride r join fetch r.driver join fetch r.vehicle where r.id = :id")
    Optional<Ride> findDetailedById(@Param("id") UUID id);

    /**
     * Ride search. Patterns must be lower-cased, LIKE-escaped and wrapped in '%'.
     * Driver and vehicle are fetched in the same query (no N+1).
     */
    @Query(value = """
            select r from Ride r join fetch r.driver join fetch r.vehicle
            where r.rideStatus = :status
              and r.availableSeats > 0
              and r.departureDate = :date
              and lower(r.source) like :source escape '\\'
              and lower(r.destination) like :destination escape '\\'
              and (r.departureDate > :today or r.departureTime > :nowTime)
            """,
            countQuery = """
            select count(r) from Ride r
            where r.rideStatus = :status
              and r.availableSeats > 0
              and r.departureDate = :date
              and lower(r.source) like :source escape '\\'
              and lower(r.destination) like :destination escape '\\'
              and (r.departureDate > :today or r.departureTime > :nowTime)
            """)
    Page<Ride> search(@Param("status") RideStatus status,
                      @Param("source") String sourcePattern,
                      @Param("destination") String destinationPattern,
                      @Param("date") LocalDate date,
                      @Param("today") LocalDate today,
                      @Param("nowTime") LocalTime nowTime,
                      Pageable pageable);

    /** All upcoming, bookable rides. */
    @Query(value = """
            select r from Ride r join fetch r.driver join fetch r.vehicle
            where r.rideStatus = :status
              and r.availableSeats > 0
              and (r.departureDate > :today or (r.departureDate = :today and r.departureTime > :nowTime))
            """,
            countQuery = """
            select count(r) from Ride r
            where r.rideStatus = :status
              and r.availableSeats > 0
              and (r.departureDate > :today or (r.departureDate = :today and r.departureTime > :nowTime))
            """)
    Page<Ride> findUpcoming(@Param("status") RideStatus status,
                            @Param("today") LocalDate today,
                            @Param("nowTime") LocalTime nowTime,
                            Pageable pageable);

    @Query(value = "select r from Ride r join fetch r.driver join fetch r.vehicle where r.driver.id = :driverId",
            countQuery = "select count(r) from Ride r where r.driver.id = :driverId")
    Page<Ride> findByDriver(@Param("driverId") UUID driverId, Pageable pageable);

    @Query(value = """
            select r from Ride r join fetch r.driver join fetch r.vehicle
            where r.driver.id = :driverId and r.rideStatus = :status
            """,
            countQuery = "select count(r) from Ride r where r.driver.id = :driverId and r.rideStatus = :status")
    Page<Ride> findByDriverAndStatus(@Param("driverId") UUID driverId, @Param("status") RideStatus status,
                                     Pageable pageable);

    @Query(value = "select r from Ride r join fetch r.driver join fetch r.vehicle",
            countQuery = "select count(r) from Ride r")
    Page<Ride> findAllDetailed(Pageable pageable);

    @Query(value = "select r from Ride r join fetch r.driver join fetch r.vehicle where r.rideStatus = :status",
            countQuery = "select count(r) from Ride r where r.rideStatus = :status")
    Page<Ride> findAllDetailedByStatus(@Param("status") RideStatus status, Pageable pageable);

    long countByRideStatus(RideStatus status);
}

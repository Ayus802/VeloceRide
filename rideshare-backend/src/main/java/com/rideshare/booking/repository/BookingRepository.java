package com.rideshare.booking.repository;

import com.rideshare.booking.entity.Booking;
import com.rideshare.common.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    String DETAILED = "select b from Booking b join fetch b.ride r join fetch r.driver join fetch b.passenger ";

    /** Lets callers lock the ride row before touching the booking (consistent lock order: ride, then booking). */
    @Query("select b.ride.id from Booking b where b.id = :id")
    Optional<UUID> findRideIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    @Query(DETAILED + "where b.id = :id")
    Optional<Booking> findDetailedById(@Param("id") UUID id);

    boolean existsByRideIdAndPassengerIdAndBookingStatusIn(UUID rideId, UUID passengerId,
                                                          Collection<BookingStatus> statuses);

    boolean existsByRideIdAndPassengerIdAndBookingStatus(UUID rideId, UUID passengerId, BookingStatus status);

    boolean existsByRideIdAndBookingStatusIn(UUID rideId, Collection<BookingStatus> statuses);

    @Query("select b from Booking b join fetch b.passenger where b.ride.id = :rideId and b.bookingStatus in :statuses")
    List<Booking> findByRideAndStatuses(@Param("rideId") UUID rideId,
                                        @Param("statuses") Collection<BookingStatus> statuses);

    // --- passenger view -------------------------------------------------------------------------------

    @Query(value = DETAILED + "where b.passenger.id = :passengerId",
            countQuery = "select count(b) from Booking b where b.passenger.id = :passengerId")
    Page<Booking> findByPassenger(@Param("passengerId") UUID passengerId, Pageable pageable);

    @Query(value = DETAILED + "where b.passenger.id = :passengerId and b.bookingStatus = :status",
            countQuery = "select count(b) from Booking b where b.passenger.id = :passengerId and b.bookingStatus = :status")
    Page<Booking> findByPassengerAndStatus(@Param("passengerId") UUID passengerId,
                                           @Param("status") BookingStatus status, Pageable pageable);

    // --- driver view (requests received on the driver's rides) ------------------------------------------

    @Query(value = DETAILED + "where r.driver.id = :driverId",
            countQuery = "select count(b) from Booking b where b.ride.driver.id = :driverId")
    Page<Booking> findReceived(@Param("driverId") UUID driverId, Pageable pageable);

    @Query(value = DETAILED + "where r.driver.id = :driverId and b.bookingStatus = :status",
            countQuery = "select count(b) from Booking b where b.ride.driver.id = :driverId and b.bookingStatus = :status")
    Page<Booking> findReceivedByStatus(@Param("driverId") UUID driverId, @Param("status") BookingStatus status,
                                       Pageable pageable);

    @Query(value = DETAILED + "where r.driver.id = :driverId and r.id = :rideId",
            countQuery = "select count(b) from Booking b where b.ride.driver.id = :driverId and b.ride.id = :rideId")
    Page<Booking> findReceivedForRide(@Param("driverId") UUID driverId, @Param("rideId") UUID rideId,
                                      Pageable pageable);

    @Query(value = DETAILED + "where r.driver.id = :driverId and r.id = :rideId and b.bookingStatus = :status",
            countQuery = """
                    select count(b) from Booking b
                    where b.ride.driver.id = :driverId and b.ride.id = :rideId and b.bookingStatus = :status""")
    Page<Booking> findReceivedForRideAndStatus(@Param("driverId") UUID driverId, @Param("rideId") UUID rideId,
                                               @Param("status") BookingStatus status, Pageable pageable);

    // --- admin -------------------------------------------------------------------------------------------

    @Query(value = DETAILED, countQuery = "select count(b) from Booking b")
    Page<Booking> findAllDetailed(Pageable pageable);

    @Query(value = DETAILED + "where b.bookingStatus = :status",
            countQuery = "select count(b) from Booking b where b.bookingStatus = :status")
    Page<Booking> findAllDetailedByStatus(@Param("status") BookingStatus status, Pageable pageable);

    long countByBookingStatus(BookingStatus status);
}

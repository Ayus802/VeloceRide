package com.rideshare.vehicle.repository;

import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.enums.VerificationStatus;
import com.rideshare.vehicle.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByIdAndDeletedFalse(UUID id);

    @EntityGraph(attributePaths = "owner")
    @Query("select v from Vehicle v where v.id = :id and v.deleted = false")
    Optional<Vehicle> findWithOwnerById(@Param("id") UUID id);

    Page<Vehicle> findByOwnerIdAndDeletedFalse(UUID ownerId, Pageable pageable);

    boolean existsByVehicleNumberAndDeletedFalse(String vehicleNumber);

    boolean existsByVehicleNumberAndDeletedFalseAndIdNot(String vehicleNumber, UUID id);

    @EntityGraph(attributePaths = "owner")
    Page<Vehicle> findByVerificationStatusAndDeletedFalse(VerificationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    Page<Vehicle> findByDeletedFalse(Pageable pageable);

    long countByVerificationStatusAndDeletedFalse(VerificationStatus status);

    long countByDeletedFalse();

    @Query("select count(r) > 0 from Ride r where r.vehicle.id = :vehicleId and r.rideStatus in :statuses")
    boolean hasRidesInStatus(@Param("vehicleId") UUID vehicleId, @Param("statuses") Collection<RideStatus> statuses);
}

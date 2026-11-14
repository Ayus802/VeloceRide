package com.rideshare.vehicle.entity;

import com.rideshare.common.entity.BaseEntity;
import com.rideshare.common.enums.VehicleType;
import com.rideshare.common.enums.VerificationStatus;
import com.rideshare.user.entity.User;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vehicles")
public class Vehicle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "vehicle_name", nullable = false, length = 100)
    private String vehicleName;

    /** Normalised: upper-case, no spaces or dashes. */
    @Column(name = "vehicle_number", nullable = false, length = 20)
    private String vehicleNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "brand", nullable = false, length = 50)
    private String brand;

    @Column(name = "model", nullable = false, length = 50)
    private String model;

    @Column(name = "color", nullable = false, length = 30)
    private String color;

    /** Number of passenger seats the vehicle can offer (driver excluded). */
    @Column(name = "seating_capacity", nullable = false)
    private Integer seatingCapacity;

    @Column(name = "registration_document_url", length = 1000)
    private String registrationDocumentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** Soft delete keeps historic rides intact while freeing the registration number. */
    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    public boolean isVerified() {
        return verificationStatus == VerificationStatus.VERIFIED;
    }
}

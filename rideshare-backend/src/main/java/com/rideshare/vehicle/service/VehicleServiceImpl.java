package com.rideshare.vehicle.service;

import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.enums.VerificationStatus;
import com.rideshare.common.exception.BusinessRuleException;
import com.rideshare.common.exception.ConflictException;
import com.rideshare.common.exception.DuplicateResourceException;
import com.rideshare.common.exception.ForbiddenException;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.common.response.PageResponse;
import com.rideshare.common.util.PageableUtils;
import com.rideshare.config.AppProperties;
import com.rideshare.security.UserPrincipal;
import com.rideshare.user.repository.UserRepository;
import com.rideshare.vehicle.dto.VehicleRequest;
import com.rideshare.vehicle.dto.VehicleResponse;
import com.rideshare.vehicle.entity.Vehicle;
import com.rideshare.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "vehicleName", "verificationStatus");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final AppProperties properties;

    @Override
    @Transactional
    public VehicleResponse addVehicle(UUID ownerId, VehicleRequest request) {
        String number = normalizeNumber(request.vehicleNumber());
        if (vehicleRepository.existsByVehicleNumberAndDeletedFalse(number)) {
            throw new DuplicateResourceException("A vehicle with this number is already registered");
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(userRepository.getReferenceById(ownerId));
        apply(vehicle, request, number);
        vehicle.setVerificationStatus(VerificationStatus.PENDING);
        return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
    }

    @Override
    @Transactional
    public VehicleResponse updateVehicle(UUID ownerId, UUID vehicleId, VehicleRequest request) {
        Vehicle vehicle = loadOwned(ownerId, vehicleId);
        String number = normalizeNumber(request.vehicleNumber());
        if (vehicleRepository.existsByVehicleNumberAndDeletedFalseAndIdNot(number, vehicleId)) {
            throw new DuplicateResourceException("A vehicle with this number is already registered");
        }
        boolean identityChanged = !number.equals(vehicle.getVehicleNumber())
                || !Objects.equals(blankToNull(request.registrationDocumentUrl()), vehicle.getRegistrationDocumentUrl());
        apply(vehicle, request, number);
        if (identityChanged) {
            // Verification covered the old registration details; they must be re-verified.
            vehicle.setVerificationStatus(VerificationStatus.PENDING);
            vehicle.setRejectionReason(null);
        }
        return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
    }

    @Override
    @Transactional
    public void deleteVehicle(UUID ownerId, UUID vehicleId) {
        Vehicle vehicle = loadOwned(ownerId, vehicleId);
        if (vehicleRepository.hasRidesInStatus(vehicleId, List.of(RideStatus.SCHEDULED, RideStatus.ONGOING))) {
            throw new ConflictException("Vehicle has scheduled or ongoing rides; cancel or complete them first");
        }
        vehicle.setDeleted(true);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> getMyVehicles(UUID ownerId, Pageable pageable) {
        Pageable page = PageableUtils.sanitize(pageable, SORTABLE, DEFAULT_SORT);
        return PageResponse.of(vehicleRepository.findByOwnerIdAndDeletedFalse(ownerId, page), VehicleResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(UserPrincipal caller, UUID vehicleId) {
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedFalse(vehicleId)
                .orElseThrow(() -> ResourceNotFoundException.of("Vehicle"));
        if (!caller.isAdmin() && !vehicle.getOwner().getId().equals(caller.id())) {
            throw new ForbiddenException("You can only view your own vehicles");
        }
        return VehicleResponse.from(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> listForVerification(VerificationStatus status, Pageable pageable) {
        Pageable page = PageableUtils.sanitize(pageable, SORTABLE, Sort.by(Sort.Direction.ASC, "createdAt"));
        return PageResponse.of(status == null
                ? vehicleRepository.findByDeletedFalse(page)
                : vehicleRepository.findByVerificationStatusAndDeletedFalse(status, page), VehicleResponse::from);
    }

    @Override
    @Transactional
    public VehicleResponse verify(UUID vehicleId) {
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedFalse(vehicleId)
                .orElseThrow(() -> ResourceNotFoundException.of("Vehicle"));
        vehicle.setVerificationStatus(VerificationStatus.VERIFIED);
        vehicle.setRejectionReason(null);
        return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
    }

    @Override
    @Transactional
    public VehicleResponse reject(UUID vehicleId, String reason) {
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedFalse(vehicleId)
                .orElseThrow(() -> ResourceNotFoundException.of("Vehicle"));
        vehicle.setVerificationStatus(VerificationStatus.REJECTED);
        vehicle.setRejectionReason(reason.trim());
        return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
    }

    @Override
    @Transactional(readOnly = true)
    public Vehicle requireUsableVehicle(UUID ownerId, UUID vehicleId) {
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedFalse(vehicleId)
                .orElseThrow(() -> ResourceNotFoundException.of("Vehicle"));
        if (!vehicle.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("You can only publish rides with your own vehicle");
        }
        if (properties.vehicle().verificationRequired() && !vehicle.isVerified()) {
            throw new BusinessRuleException("Vehicle must be verified by an admin before it can be used to publish rides");
        }
        return vehicle;
    }

    private Vehicle loadOwned(UUID ownerId, UUID vehicleId) {
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedFalse(vehicleId)
                .orElseThrow(() -> ResourceNotFoundException.of("Vehicle"));
        if (!vehicle.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("You can only modify your own vehicles");
        }
        return vehicle;
    }

    private static void apply(Vehicle vehicle, VehicleRequest request, String normalizedNumber) {
        vehicle.setVehicleName(request.vehicleName().trim());
        vehicle.setVehicleNumber(normalizedNumber);
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setBrand(request.brand().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setColor(request.color().trim());
        vehicle.setSeatingCapacity(request.seatingCapacity());
        vehicle.setRegistrationDocumentUrl(blankToNull(request.registrationDocumentUrl()));
    }

    static String normalizeNumber(String number) {
        return number.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

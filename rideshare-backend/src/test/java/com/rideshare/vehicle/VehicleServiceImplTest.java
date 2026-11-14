package com.rideshare.vehicle;

import com.rideshare.common.enums.VerificationStatus;
import com.rideshare.common.exception.BusinessRuleException;
import com.rideshare.common.exception.ForbiddenException;
import com.rideshare.config.AppProperties;
import com.rideshare.support.Fixtures;
import com.rideshare.user.entity.User;
import com.rideshare.user.repository.UserRepository;
import com.rideshare.vehicle.entity.Vehicle;
import com.rideshare.vehicle.repository.VehicleRepository;
import com.rideshare.vehicle.service.VehicleServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock
    VehicleRepository vehicleRepository;
    @Mock
    UserRepository userRepository;

    private VehicleServiceImpl service(boolean verificationRequired) {
        AppProperties props = new AppProperties(null, null, null, null,
                new AppProperties.Vehicle(verificationRequired), null, null, null, null, null, null, null);
        return new VehicleServiceImpl(vehicleRepository, userRepository, props);
    }

    @Test
    void unverifiedVehicleCannotPublishWhenVerificationRequired() {
        User owner = Fixtures.user("Owner");
        Vehicle vehicle = Fixtures.vehicle(owner, 4);
        when(vehicleRepository.findByIdAndDeletedFalse(vehicle.getId())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service(true).requireUsableVehicle(owner.getId(), vehicle.getId()))
                .isInstanceOf(BusinessRuleException.class);

        vehicle.setVerificationStatus(VerificationStatus.VERIFIED);
        assertThat(service(true).requireUsableVehicle(owner.getId(), vehicle.getId())).isSameAs(vehicle);
    }

    @Test
    void verificationCanBeDisabledForDevelopment() {
        User owner = Fixtures.user("Owner");
        Vehicle vehicle = Fixtures.vehicle(owner, 4);
        when(vehicleRepository.findByIdAndDeletedFalse(vehicle.getId())).thenReturn(Optional.of(vehicle));
        assertThat(service(false).requireUsableVehicle(owner.getId(), vehicle.getId())).isSameAs(vehicle);
    }

    @Test
    void cannotUseAnotherUsersVehicle() {
        User owner = Fixtures.user("Owner");
        Vehicle vehicle = Fixtures.vehicle(owner, 4);
        when(vehicleRepository.findByIdAndDeletedFalse(vehicle.getId())).thenReturn(Optional.of(vehicle));
        assertThatThrownBy(() -> service(false).requireUsableVehicle(Fixtures.user("Other").getId(), vehicle.getId()))
                .isInstanceOf(ForbiddenException.class);
    }
}

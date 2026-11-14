package com.rideshare.common;

import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.enums.RideStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusTransitionTest {

    @Test
    void bookingTransitions() {
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.ACCEPTED)).isTrue();
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.REJECTED)).isTrue();
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.COMPLETED)).isFalse();
        assertThat(BookingStatus.ACCEPTED.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
        assertThat(BookingStatus.ACCEPTED.canTransitionTo(BookingStatus.COMPLETED)).isTrue();
        assertThat(BookingStatus.ACCEPTED.canTransitionTo(BookingStatus.REJECTED)).isFalse();
        for (BookingStatus terminal : new BookingStatus[]{BookingStatus.REJECTED, BookingStatus.CANCELLED, BookingStatus.COMPLETED}) {
            for (BookingStatus target : BookingStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).isFalse();
            }
        }
    }

    @Test
    void rideTransitions() {
        assertThat(RideStatus.SCHEDULED.canTransitionTo(RideStatus.ONGOING)).isTrue();
        assertThat(RideStatus.SCHEDULED.canTransitionTo(RideStatus.CANCELLED)).isTrue();
        assertThat(RideStatus.SCHEDULED.canTransitionTo(RideStatus.COMPLETED)).isFalse();
        assertThat(RideStatus.ONGOING.canTransitionTo(RideStatus.COMPLETED)).isTrue();
        assertThat(RideStatus.ONGOING.canTransitionTo(RideStatus.CANCELLED)).isFalse();
        assertThat(RideStatus.COMPLETED.canTransitionTo(RideStatus.ONGOING)).isFalse();
        assertThat(RideStatus.CANCELLED.canTransitionTo(RideStatus.SCHEDULED)).isFalse();
    }
}

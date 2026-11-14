package com.rideshare.common.event;

import com.rideshare.common.enums.PaymentMethod;
import com.rideshare.common.enums.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Published when a payment reaches SUCCESS, FAILED or REFUNDED. */
public record PaymentEvent(
        UUID paymentId,
        PaymentStatus status,
        UUID bookingId,
        String bookingReference,
        UUID payerId,
        UUID driverId,
        BigDecimal amount,
        PaymentMethod method) {
}

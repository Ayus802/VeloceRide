package com.rideshare.payment.dto;

import com.rideshare.common.enums.PaymentMethod;
import com.rideshare.common.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID bookingId,
        String bookingReference,
        UUID payerId,
        @Schema(example = "1300.00") BigDecimal amount,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        @Schema(example = "TXN-8M2KQ7XRC4PZ") String transactionReference,
        @Schema(example = "SIMULATED") String provider,
        String providerReference,
        String failureReason,
        @Schema(description = "What the client should do next (present only right after initiation)")
        String instructions,
        Instant createdAt,
        Instant updatedAt) {
}

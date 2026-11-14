package com.rideshare.payment.dto;

import com.rideshare.common.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Start paying for an ACCEPTED booking. The amount is always taken from the booking.")
public record InitiatePaymentRequest(
        @NotNull UUID bookingId,
        @Schema(example = "UPI") @NotNull PaymentMethod paymentMethod) {
}

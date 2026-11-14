package com.rideshare.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Ask the server to verify a payment with the provider. There is deliberately no 'success' "
        + "field: the outcome is decided by server-side verification. CASH payments are confirmed by the DRIVER "
        + "after collecting the cash.")
public record ConfirmPaymentRequest(
        @NotNull UUID paymentId,
        @Schema(description = "Provider payment id from the checkout callback (e.g. razorpay_payment_id). "
                + "Simulated provider: 'SIMULATE_FAILURE' simulates a decline.")
        @Size(max = 100) String providerPaymentId,
        @Schema(description = "Provider signature from the checkout callback (e.g. razorpay_signature)")
        @Size(max = 512) String providerSignature) {
}

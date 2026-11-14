package com.rideshare.payment.controller;

import com.rideshare.common.response.ApiResponse;
import com.rideshare.common.response.PageResponse;
import com.rideshare.payment.dto.ConfirmPaymentRequest;
import com.rideshare.payment.dto.InitiatePaymentRequest;
import com.rideshare.payment.dto.PaymentResponse;
import com.rideshare.payment.service.PaymentService;
import com.rideshare.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "6. Payments", description = "Simulated payment flow behind a provider abstraction. No real money moves "
        + "with the SIMULATED provider.")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @Operation(summary = "Initiate a payment for an accepted booking",
            description = "Passenger only. Amount comes from the booking. Returns a PENDING payment and instructions.")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiate(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @Valid @RequestBody InitiatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment initiated", paymentService.initiate(principal.id(), request)));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm a payment (server-side verification)",
            description = "Online methods: called by the payer; the provider verifies the result. "
                    + "CASH: called by the driver after receiving the cash.")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirm(@AuthenticationPrincipal UserPrincipal principal,
                                                                @Valid @RequestBody ConfirmPaymentRequest request) {
        PaymentResponse response = paymentService.confirm(principal.id(), request);
        String message = switch (response.paymentStatus()) {
            case SUCCESS -> "Payment successful";
            case FAILED -> "Payment failed";
            case PENDING -> "Payment is still pending";
            case REFUNDED -> "Payment refunded";
        };
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @GetMapping("/my-payments")
    @Operation(summary = "Payments I made")
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> mine(
            @AuthenticationPrincipal UserPrincipal principal, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved",
                paymentService.myPayments(principal.id(), pageable)));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get a payment", description = "Payer, ride driver or admin only.")
    public ResponseEntity<ApiResponse<PaymentResponse>> get(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.success("Payment retrieved", paymentService.get(principal, paymentId)));
    }
}

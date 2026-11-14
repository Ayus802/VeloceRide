package com.rideshare.payment.gateway;

import java.math.BigDecimal;

/**
 * Port to an external payment provider. Business rules live in {@code PaymentServiceImpl}; implementations
 * only talk to the provider. To integrate Razorpay (or similar), implement this interface:
 * <ul>
 *   <li>{@link #createOrder} - create a provider order and return its id (client uses it in the checkout widget)</li>
 *   <li>{@link #verify} - verify the provider callback server-side (e.g. Razorpay HMAC signature + fetch status)</li>
 *   <li>{@link #refund} - issue a refund through the provider API</li>
 * </ul>
 * and select it with {@code app.payment.provider}.
 */
public interface PaymentGateway {

    /** Value stored in {@code payments.provider}. */
    String providerName();

    /** Creates a provider order for the given amount. */
    GatewayOrder createOrder(String transactionReference, BigDecimal amount, String currency);

    /**
     * Verifies, on the server, the outcome of a payment attempt. The client-supplied identifiers are only
     * inputs to verification; they are never trusted as a success flag on their own.
     */
    GatewayVerification verify(String providerOrderId, String providerPaymentId, String providerSignature);

    GatewayRefund refund(String providerOrderId, BigDecimal amount);

    record GatewayOrder(String providerOrderId, String clientInstructions) {
    }

    record GatewayVerification(Outcome outcome, String providerPaymentId, String failureReason) {
    }

    record GatewayRefund(boolean success, String providerRefundId, String failureReason) {
    }

    enum Outcome {
        SUCCESS,
        FAILED,
        PENDING
    }
}

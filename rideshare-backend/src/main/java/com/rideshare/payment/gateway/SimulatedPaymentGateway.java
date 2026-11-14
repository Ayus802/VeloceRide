package com.rideshare.payment.gateway;

import com.rideshare.common.util.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Development/test gateway. NO REAL MONEY MOVES and no bank/UPI/card network is contacted.
 * <p>Behaves like a sandbox: verification succeeds unless the client passes the documented test value
 * {@value #FAILURE_TEST_PAYMENT_ID} as {@code providerPaymentId} (analogous to a sandbox "decline" test card).
 * The success decision is made here on the server, not by a client-provided success flag.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.payment", name = "provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGateway {

    public static final String PROVIDER = "SIMULATED";
    public static final String FAILURE_TEST_PAYMENT_ID = "SIMULATE_FAILURE";

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public GatewayOrder createOrder(String transactionReference, BigDecimal amount, String currency) {
        String orderId = "sim_order_" + TokenUtils.randomToken(12);
        log.info("[SIMULATED] Created order {} for {} {} (ref {})", orderId, amount, currency, transactionReference);
        return new GatewayOrder(orderId, "SIMULATED PAYMENT - no real money is charged. Call POST /api/payments/confirm "
                + "with this paymentId to complete. Use providerPaymentId='" + FAILURE_TEST_PAYMENT_ID
                + "' to simulate a declined payment.");
    }

    @Override
    public GatewayVerification verify(String providerOrderId, String providerPaymentId, String providerSignature) {
        if (FAILURE_TEST_PAYMENT_ID.equals(providerPaymentId)) {
            return new GatewayVerification(Outcome.FAILED, null, "Payment declined by simulated provider");
        }
        String paymentId = "sim_pay_" + TokenUtils.randomToken(12);
        log.info("[SIMULATED] Verified order {} as paid ({})", providerOrderId, paymentId);
        return new GatewayVerification(Outcome.SUCCESS, paymentId, null);
    }

    @Override
    public GatewayRefund refund(String providerOrderId, BigDecimal amount) {
        log.info("[SIMULATED] Refunded {} for order {}", amount, providerOrderId);
        return new GatewayRefund(true, "sim_refund_" + TokenUtils.randomToken(12), null);
    }
}

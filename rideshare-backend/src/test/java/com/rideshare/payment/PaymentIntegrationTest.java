package com.rideshare.payment;

import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentIntegrationTest extends AbstractIntegrationTest {

    private record Paid(RideFixture ride, TestUser passenger, UUID bookingId) {
    }

    private Paid acceptedBooking() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser passenger = registerUser();
        UUID booking = requestBooking(passenger, f.rideId(), 2);
        accept(f.driver(), booking);
        return new Paid(f, passenger, booking);
    }

    @Test
    void onlinePaymentIsVerifiedServerSide() throws Exception {
        Paid p = acceptedBooking();
        UUID paymentId = id(data(doPost("/api/payments/initiate", p.passenger(),
                Map.of("bookingId", p.bookingId(), "paymentMethod", "UPI"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.amount").value(1000.00))
                .andExpect(jsonPath("$.data.provider").value("SIMULATED"))
                .andExpect(jsonPath("$.data.instructions").isNotEmpty())));

        // Someone else cannot confirm it.
        doPost("/api/payments/confirm", p.ride().driver(), Map.of("paymentId", paymentId)).andExpect(status().isForbidden());

        doPost("/api/payments/confirm", p.passenger(), Map.of("paymentId", paymentId, "providerPaymentId", "pay_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment successful"))
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"));

        doPost("/api/payments/confirm", p.passenger(), Map.of("paymentId", paymentId)).andExpect(status().isConflict());
        doPost("/api/payments/initiate", p.passenger(), Map.of("bookingId", p.bookingId(), "paymentMethod", "CARD"))
                .andExpect(status().isConflict());

        doGet("/api/payments/my-payments", p.passenger()).andExpect(jsonPath("$.data.totalElements").value(1));
        doGet("/api/payments/" + paymentId, p.ride().driver()).andExpect(status().isOk());
        doGet("/api/payments/" + paymentId, registerUser()).andExpect(status().isForbidden());
    }

    @Test
    void declinedPaymentIsMarkedFailed() throws Exception {
        Paid p = acceptedBooking();
        UUID paymentId = id(data(doPost("/api/payments/initiate", p.passenger(),
                Map.of("bookingId", p.bookingId(), "paymentMethod", "CARD"))));
        doPost("/api/payments/confirm", p.passenger(),
                Map.of("paymentId", paymentId, "providerPaymentId", "SIMULATE_FAILURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("FAILED"));
        doGet("/api/notifications?read=false", p.passenger())
                .andExpect(jsonPath("$.data.content[0].notificationType").value("PAYMENT_FAILED"));
        // A new attempt is allowed after a failure.
        doPost("/api/payments/initiate", p.passenger(), Map.of("bookingId", p.bookingId(), "paymentMethod", "UPI"))
                .andExpect(status().isCreated());
    }

    @Test
    void cashIsConfirmedByDriverOnly() throws Exception {
        Paid p = acceptedBooking();
        UUID paymentId = id(data(doPost("/api/payments/initiate", p.passenger(),
                Map.of("bookingId", p.bookingId(), "paymentMethod", "CASH"))));
        doPost("/api/payments/confirm", p.passenger(), Map.of("paymentId", paymentId)).andExpect(status().isForbidden());
        doPost("/api/payments/confirm", p.ride().driver(), Map.of("paymentId", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"));
    }

    @Test
    void cannotPayForPendingOrSomeoneElsesBooking() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser passenger = registerUser();
        UUID pending = requestBooking(passenger, f.rideId(), 1);
        doPost("/api/payments/initiate", passenger, Map.of("bookingId", pending, "paymentMethod", "UPI"))
                .andExpect(status().isConflict());
        doPost("/api/payments/initiate", registerUser(), Map.of("bookingId", pending, "paymentMethod", "UPI"))
                .andExpect(status().isForbidden());
    }

    @Test
    void successfulPaymentIsRefundedWhenBookingCancelled() throws Exception {
        Paid p = acceptedBooking();
        UUID paymentId = id(data(doPost("/api/payments/initiate", p.passenger(),
                Map.of("bookingId", p.bookingId(), "paymentMethod", "UPI"))));
        doPost("/api/payments/confirm", p.passenger(), Map.of("paymentId", paymentId)).andExpect(status().isOk());
        doPatch("/api/bookings/" + p.bookingId() + "/cancel", p.passenger(), null).andExpect(status().isOk());
        doGet("/api/payments/" + paymentId, p.passenger())
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));
    }
}

package com.rideshare.notification.service;

import com.rideshare.common.enums.NotificationType;
import com.rideshare.common.event.BookingEvent;
import com.rideshare.common.event.PaymentEvent;
import com.rideshare.common.event.RideEvent;
import com.rideshare.common.event.ReviewEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns business events into notifications. Runs synchronously inside the publishing transaction, so a
 * notification is stored if and only if the business change commits. Controllers and the booking/ride/payment
 * services do not know this class exists.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notifications;

    @EventListener
    public void onBooking(BookingEvent e) {
        String seats = e.numberOfSeats() + " seat(s)";
        switch (e.type()) {
            case REQUESTED -> notifications.notify(e.driverId(), NotificationType.BOOKING_REQUEST,
                    "New booking request",
                    e.passengerName() + " requested " + seats + " on " + e.route() + " (" + e.bookingReference() + ")",
                    e.bookingId());
            case ACCEPTED -> notifications.notify(e.passengerId(), NotificationType.BOOKING_ACCEPTED,
                    "Booking accepted",
                    "Your request for " + seats + " on " + e.route() + " was accepted (" + e.bookingReference() + ")",
                    e.bookingId());
            case REJECTED -> notifications.notify(e.passengerId(), NotificationType.BOOKING_REJECTED,
                    "Booking rejected",
                    "Your request for " + seats + " on " + e.route() + " was declined by the driver",
                    e.bookingId());
            case AUTO_REJECTED -> notifications.notify(e.passengerId(), NotificationType.BOOKING_REJECTED,
                    "Booking request expired",
                    "The ride " + e.route() + " started before your request was accepted",
                    e.bookingId());
            case CANCELLED_BY_PASSENGER -> notifications.notify(e.driverId(), NotificationType.BOOKING_CANCELLED,
                    "Booking cancelled",
                    e.passengerName() + " cancelled their booking (" + seats + ") on " + e.route(),
                    e.bookingId());
            case CANCELLED_BY_RIDE -> notifications.notify(e.passengerId(), NotificationType.RIDE_CANCELLED,
                    "Ride cancelled",
                    "The driver cancelled the ride " + e.route() + ". Your booking " + e.bookingReference()
                            + " was cancelled and any payment will be refunded.",
                    e.bookingId());
            case COMPLETED -> {
                // covered by the RIDE_COMPLETED notification
            }
        }
    }

    @EventListener
    public void onRide(RideEvent e) {
        switch (e.type()) {
            case STARTED -> e.passengerIds().forEach(p -> notifications.notify(p, NotificationType.RIDE_STARTED,
                    "Ride started", "Your ride " + e.route() + " has started. You can now track the driver live.",
                    e.rideId()));
            case COMPLETED -> {
                e.passengerIds().forEach(p -> notifications.notify(p, NotificationType.RIDE_COMPLETED,
                        "Ride completed", "Your ride " + e.route() + " is complete. Please rate your driver.",
                        e.rideId()));
                notifications.notify(e.driverId(), NotificationType.RIDE_COMPLETED, "Ride completed",
                        "Your ride " + e.route() + " is complete. Please rate your passengers.", e.rideId());
            }
            case CANCELLED -> {
                // passengers are notified per booking (CANCELLED_BY_RIDE)
            }
        }
    }

    @EventListener
    public void onPayment(PaymentEvent e) {
        String amount = e.amount() + " (" + e.method() + ")";
        switch (e.status()) {
            case SUCCESS -> {
                notifications.notify(e.payerId(), NotificationType.PAYMENT_SUCCESS, "Payment successful",
                        "Your payment of " + amount + " for booking " + e.bookingReference() + " succeeded",
                        e.paymentId());
                notifications.notify(e.driverId(), NotificationType.PAYMENT_SUCCESS, "Payment received",
                        "Payment of " + amount + " received for booking " + e.bookingReference(), e.paymentId());
            }
            case FAILED -> notifications.notify(e.payerId(), NotificationType.PAYMENT_FAILED, "Payment failed",
                    "Your payment of " + amount + " for booking " + e.bookingReference() + " failed",
                    e.paymentId());
            case REFUNDED -> notifications.notify(e.payerId(), NotificationType.SYSTEM, "Payment refunded",
                    "Your payment of " + amount + " for booking " + e.bookingReference() + " has been refunded",
                    e.paymentId());
            case PENDING -> {
                // not published
            }
        }
    }

    @EventListener
    public void onReview(ReviewEvent e) {
        notifications.notify(e.reviewedUserId(), NotificationType.NEW_REVIEW, "New review",
                e.reviewerName() + " rated you " + e.rating() + "/5", e.reviewId());
    }
}

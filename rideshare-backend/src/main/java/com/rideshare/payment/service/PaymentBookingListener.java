package com.rideshare.payment.service;

import com.rideshare.common.event.BookingEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Keeps payments consistent with booking cancellations without the booking module depending on payments. */
@Component
@RequiredArgsConstructor
public class PaymentBookingListener {

    private final PaymentService paymentService;

    @EventListener
    public void onBookingEvent(BookingEvent event) {
        if (event.isCancellation()) {
            paymentService.handleBookingCancelled(event.bookingId());
        }
    }
}

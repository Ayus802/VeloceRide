package com.rideshare.payment.service;

import com.rideshare.common.response.PageResponse;
import com.rideshare.payment.dto.ConfirmPaymentRequest;
import com.rideshare.payment.dto.InitiatePaymentRequest;
import com.rideshare.payment.dto.PaymentResponse;
import com.rideshare.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PaymentService {

    PaymentResponse initiate(UUID payerId, InitiatePaymentRequest request);

    PaymentResponse confirm(UUID callerId, ConfirmPaymentRequest request);

    PaymentResponse get(UserPrincipal caller, UUID paymentId);

    PageResponse<PaymentResponse> myPayments(UUID payerId, Pageable pageable);

    /** Refunds a successful payment / voids a pending one when the booking is cancelled. */
    void handleBookingCancelled(UUID bookingId);
}

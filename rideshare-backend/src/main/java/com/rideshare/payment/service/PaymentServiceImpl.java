package com.rideshare.payment.service;

import com.rideshare.booking.entity.Booking;
import com.rideshare.booking.repository.BookingRepository;
import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.enums.PaymentMethod;
import com.rideshare.common.enums.PaymentStatus;
import com.rideshare.common.event.PaymentEvent;
import com.rideshare.common.exception.ConflictException;
import com.rideshare.common.exception.ForbiddenException;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.common.response.PageResponse;
import com.rideshare.common.util.PageableUtils;
import com.rideshare.common.util.TokenUtils;
import com.rideshare.payment.dto.ConfirmPaymentRequest;
import com.rideshare.payment.dto.InitiatePaymentRequest;
import com.rideshare.payment.dto.PaymentResponse;
import com.rideshare.payment.entity.Payment;
import com.rideshare.payment.gateway.PaymentGateway;
import com.rideshare.payment.repository.PaymentRepository;
import com.rideshare.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Payment business rules. Talks to the provider only through {@link PaymentGateway}.
 * Amounts always come from the booking; success is only recorded after server-side verification
 * (or, for CASH, after the driver confirms receipt).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    static final String CURRENCY = "INR";
    static final String CASH_PROVIDER = "CASH";
    private static final Set<BookingStatus> PAYABLE = Set.of(BookingStatus.ACCEPTED, BookingStatus.COMPLETED);

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final PaymentGateway gateway;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public PaymentResponse initiate(UUID payerId, InitiatePaymentRequest request) {
        Booking booking = bookingRepository.findByIdForUpdate(request.bookingId())
                .orElseThrow(() -> ResourceNotFoundException.of("Booking"));
        if (!booking.isOwnedBy(payerId)) {
            throw new ForbiddenException("You can only pay for your own bookings");
        }
        if (!PAYABLE.contains(booking.getBookingStatus())) {
            throw new ConflictException("Payment is only possible for accepted bookings");
        }
        List<Payment> live = paymentRepository.findByBookingAndStatusesForUpdate(booking.getId(),
                List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS));
        if (live.stream().anyMatch(p -> p.getPaymentStatus() == PaymentStatus.SUCCESS)) {
            throw new ConflictException("This booking has already been paid");
        }
        for (Payment stale : live) { // only PENDING ones remain: supersede them
            stale.setPaymentStatus(PaymentStatus.FAILED);
            stale.setFailureReason("Superseded by a new payment attempt");
            paymentRepository.saveAndFlush(stale); // flush before inserting the new row (partial unique index)
        }

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPayer(booking.getPassenger());
        payment.setAmount(booking.getTotalAmount());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setTransactionReference(TokenUtils.reference("TXN", 12));
        String instructions;
        if (request.paymentMethod() == PaymentMethod.CASH) {
            payment.setProvider(CASH_PROVIDER);
            instructions = "Pay " + booking.getTotalAmount() + " " + CURRENCY
                    + " in cash to the driver. The driver confirms receipt via POST /api/payments/confirm.";
        } else {
            PaymentGateway.GatewayOrder order = gateway.createOrder(payment.getTransactionReference(),
                    booking.getTotalAmount(), CURRENCY);
            payment.setProvider(gateway.providerName());
            payment.setProviderReference(order.providerOrderId());
            instructions = order.clientInstructions();
        }
        paymentRepository.saveAndFlush(payment);
        return toResponse(payment, instructions);
    }

    @Override
    @Transactional
    public PaymentResponse confirm(UUID callerId, ConfirmPaymentRequest request) {
        Payment payment = paymentRepository.findByIdForUpdate(request.paymentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Payment"));
        Booking booking = payment.getBooking();
        UUID driverId = booking.getRide().getDriver().getId();
        UUID payerId = payment.getPayer().getId();

        boolean cash = payment.getPaymentMethod() == PaymentMethod.CASH;
        if (cash && !callerId.equals(driverId)) {
            throw new ForbiddenException("Cash payments are confirmed by the driver after receiving the cash");
        }
        if (!cash && !callerId.equals(payerId)) {
            throw new ForbiddenException("You can only confirm your own payments");
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ConflictException("Payment is already " + payment.getPaymentStatus());
        }
        if (!PAYABLE.contains(booking.getBookingStatus())) {
            fail(payment, "Booking is no longer active");
            return toResponse(payment, null);
        }

        if (cash) {
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setProviderReference("cash-confirmed-by-driver");
            publish(payment, driverId);
        } else {
            PaymentGateway.GatewayVerification result = gateway.verify(payment.getProviderReference(),
                    request.providerPaymentId(), request.providerSignature());
            switch (result.outcome()) {
                case SUCCESS -> {
                    payment.setPaymentStatus(PaymentStatus.SUCCESS);
                    payment.setProviderReference(result.providerPaymentId());
                    payment.setFailureReason(null);
                    publish(payment, driverId);
                }
                case FAILED -> fail(payment, result.failureReason());
                case PENDING -> log.info("Payment {} still pending at provider", payment.getId());
            }
        }
        paymentRepository.saveAndFlush(payment);
        return toResponse(payment, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse get(UserPrincipal caller, UUID paymentId) {
        Payment payment = paymentRepository.findDetailedById(paymentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Payment"));
        boolean payer = payment.getPayer().getId().equals(caller.id());
        boolean driver = payment.getBooking().getRide().isDrivenBy(caller.id());
        if (!payer && !driver && !caller.isAdmin()) {
            throw new ForbiddenException("You can only view payments you made or received");
        }
        return toResponse(payment, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> myPayments(UUID payerId, Pageable pageable) {
        Pageable page = PageableUtils.sanitize(pageable, Set.of("createdAt", "amount", "paymentStatus"),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(paymentRepository.findByPayer(payerId, page), p -> toResponse(p, null));
    }

    @Override
    @Transactional
    public void handleBookingCancelled(UUID bookingId) {
        List<Payment> live = paymentRepository.findByBookingAndStatusesForUpdate(bookingId,
                List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS));
        for (Payment locked : live) {
            UUID driverId = locked.getBooking().getRide().getDriver().getId();
            if (locked.getPaymentStatus() == PaymentStatus.PENDING) {
                locked.setPaymentStatus(PaymentStatus.FAILED);
                locked.setFailureReason("Booking was cancelled");
            } else if (locked.getPaymentStatus() == PaymentStatus.SUCCESS) {
                if (locked.getPaymentMethod() != PaymentMethod.CASH) {
                    PaymentGateway.GatewayRefund refund = gateway.refund(locked.getProviderReference(), locked.getAmount());
                    if (!refund.success()) {
                        log.error("Refund failed for payment {}: {}", locked.getId(), refund.failureReason());
                        continue; // stays SUCCESS; needs manual follow-up
                    }
                }
                locked.setPaymentStatus(PaymentStatus.REFUNDED);
                publish(locked, driverId);
            }
            paymentRepository.saveAndFlush(locked);
        }
    }

    private void fail(Payment payment, String reason) {
        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        paymentRepository.saveAndFlush(payment);
        publish(payment, payment.getBooking().getRide().getDriver().getId());
    }

    private void publish(Payment p, UUID driverId) {
        events.publishEvent(new PaymentEvent(p.getId(), p.getPaymentStatus(), p.getBooking().getId(),
                p.getBooking().getBookingReference(), p.getPayer().getId(), driverId, p.getAmount(),
                p.getPaymentMethod()));
    }

    private static PaymentResponse toResponse(Payment p, String instructions) {
        return new PaymentResponse(p.getId(), p.getBooking().getId(), p.getBooking().getBookingReference(),
                p.getPayer().getId(), p.getAmount(), p.getPaymentMethod(), p.getPaymentStatus(),
                p.getTransactionReference(), p.getProvider(), p.getProviderReference(), p.getFailureReason(),
                instructions, p.getCreatedAt(), p.getUpdatedAt());
    }
}

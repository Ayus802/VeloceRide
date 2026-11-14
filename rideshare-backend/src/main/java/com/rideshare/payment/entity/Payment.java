package com.rideshare.payment.entity;

import com.rideshare.booking.entity.Booking;
import com.rideshare.common.entity.BaseEntity;
import com.rideshare.common.enums.PaymentMethod;
import com.rideshare.common.enums.PaymentStatus;
import com.rideshare.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    /** Always taken from the booking on the server, never from the client. */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    /** Our own reference, shared with the provider as the merchant order id. */
    @Column(name = "transaction_reference", nullable = false, unique = true, length = 64)
    private String transactionReference;

    /** Which provider handled the payment (e.g. SIMULATED, CASH, RAZORPAY). */
    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    /** Provider-side order/payment id. */
    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}

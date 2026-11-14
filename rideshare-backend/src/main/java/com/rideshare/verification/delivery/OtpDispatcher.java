package com.rideshare.verification.delivery;

import com.rideshare.common.enums.VerificationChannel;

/** Delivers one-time codes. Implementations must never log the code outside an explicit dev mode. */
public interface OtpDispatcher {

    /** Whether a code for this channel can currently be delivered (a provider or dev logging is configured). */
    boolean canDeliver(VerificationChannel channel);

    /**
     * @param destination email address or E.164-style phone number
     * @param recipientName the user's name, for a friendly greeting
     */
    void send(VerificationChannel channel, String destination, String recipientName, String code);
}

package com.rideshare.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Secure random token / reference generation and hashing.
 */
public final class TokenUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] REFERENCE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private TokenUtils() {
    }

    /** URL-safe random token with the given number of random bytes. */
    public static String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /** Human-friendly reference such as {@code BK-7KQ2M9XA}. Ambiguous characters (0/O, 1/I) are excluded. */
    public static String reference(String prefix, int length) {
        StringBuilder sb = new StringBuilder(prefix).append('-');
        for (int i = 0; i < length; i++) {
            sb.append(REFERENCE_ALPHABET[RANDOM.nextInt(REFERENCE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

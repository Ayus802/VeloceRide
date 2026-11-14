package com.rideshare.auth.google;

/** Verifies a Google Sign-In ID token and returns the identity it asserts. */
public interface GoogleIdTokenVerifier {

    /**
     * @throws com.rideshare.common.exception.UnauthorizedException when the token is invalid, expired or for another app
     * @throws com.rideshare.common.exception.ServiceUnavailableException when Google sign-in is not configured
     */
    GoogleIdentity verify(String idToken);

    record GoogleIdentity(String subject, String email, boolean emailVerified, String name, String picture) {
    }
}

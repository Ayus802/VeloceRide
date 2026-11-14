package com.rideshare.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <accessToken>}.
 * Invalid tokens do not short-circuit here; the entry point returns a 401 if the endpoint needs auth.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "rideshare.auth.error";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        resolveToken(request).ifPresent(token -> authenticate(token, request));
        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        tokenProvider.validateAccessToken(token).ifPresentOrElse(userId -> {
            Optional<UserPrincipal> principal = userDetailsService.loadUserById(userId);
            if (principal.isEmpty()) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Invalid or expired token");
                return;
            }
            if (!principal.get().isEnabled()) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Account is deactivated");
                return;
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal.get(), null, principal.get().getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }, () -> request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Invalid or expired token"));
    }

    public static Optional<String> extractBearer(String headerValue) {
        if (headerValue != null && headerValue.startsWith(BEARER_PREFIX)) {
            String token = headerValue.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? Optional.empty() : Optional.of(token);
        }
        return Optional.empty();
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        return extractBearer(request.getHeader(HttpHeaders.AUTHORIZATION));
    }
}

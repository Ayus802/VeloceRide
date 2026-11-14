package com.rideshare.security;

import com.rideshare.location.service.LocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates and authorizes every inbound STOMP frame.
 * <ul>
 *   <li>CONNECT must carry a valid access token in the {@code Authorization: Bearer ...} STOMP header.</li>
 *   <li>SUBSCRIBE is allowed only to {@code /user/queue/notifications}, {@code /user/queue/errors}, and
 *       {@code /topic/rides/{rideId}/location|status} for the ride's driver or passengers with an ACCEPTED
 *       booking. Every other destination is refused, so nobody can listen to arbitrary rides.</li>
 *   <li>SEND is allowed only to application destinations ({@code /app/**}); clients cannot publish to topics.</li>
 * </ul>
 */
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern RIDE_TOPIC =
            Pattern.compile("^/topic/rides/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/(location|status)$");
    private static final Set<String> ALLOWED_USER_QUEUES = Set.of("/user/queue/notifications", "/user/queue/errors");

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final LocationService locationService;

    /** LocationService is lazy to break the cycle WebSocket config -> interceptor -> service -> messaging template. */
    public StompAuthChannelInterceptor(JwtTokenProvider tokenProvider,
                                       CustomUserDetailsService userDetailsService,
                                       @Lazy LocationService locationService) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.locationService = locationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> authorizeSubscription(requireUser(accessor), accessor.getDestination());
            case SEND -> {
                requireUser(accessor);
                String destination = accessor.getDestination();
                if (destination == null || !destination.startsWith("/app/")) {
                    throw new AccessDeniedException("Clients may only send to /app destinations");
                }
            }
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK, NACK ... need no extra checks
            }
        }
        return message;
    }

    private UsernamePasswordAuthenticationToken authenticate(StompHeaderAccessor accessor) {
        String header = firstHeader(accessor, "Authorization");
        if (header == null) {
            header = firstHeader(accessor, "authorization");
        }
        String token = JwtAuthenticationFilter.extractBearer(header)
                .orElseThrow(() -> new BadCredentialsException("Missing bearer token in STOMP CONNECT headers"));
        UUID userId = tokenProvider.validateAccessToken(token)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired token"));
        UserPrincipal principal = userDetailsService.loadUserById(userId)
                .filter(UserPrincipal::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Account not found or deactivated"));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private UserPrincipal requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user instanceof UsernamePasswordAuthenticationToken auth && auth.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        throw new AccessDeniedException("Not authenticated");
    }

    private void authorizeSubscription(UserPrincipal user, String destination) {
        if (destination == null) {
            throw new AccessDeniedException("Missing destination");
        }
        if (ALLOWED_USER_QUEUES.contains(destination)) {
            return;
        }
        Matcher matcher = RIDE_TOPIC.matcher(destination);
        if (matcher.matches() && locationService.canSubscribe(user.id(), UUID.fromString(matcher.group(1)))) {
            return;
        }
        log.debug("Denied STOMP subscription of user {} to {}", user.id(), destination);
        throw new AccessDeniedException("You are not allowed to subscribe to " + destination);
    }

    private static String firstHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}

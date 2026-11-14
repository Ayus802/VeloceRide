package com.rideshare.location.controller;

import com.rideshare.common.exception.ApiException;
import com.rideshare.common.response.ApiResponse;
import com.rideshare.location.dto.LocationUpdateRequest;
import com.rideshare.location.service.LocationService;
import com.rideshare.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP endpoint for drivers: SEND to {@code /app/location/update}.
 * Errors are sent privately to the sender on {@code /user/queue/errors}.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LocationWebSocketController {

    private final LocationService locationService;

    @MessageMapping("/location/update")
    public void updateLocation(@Payload LocationUpdateRequest request, Principal principal) {
        locationService.publishUpdate(principalOf(principal).id(), request);
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiResponse<Void> handleError(Exception ex) {
        if (ex instanceof ApiException apiException) {
            return ApiResponse.error(apiException.getMessage());
        }
        if (ex instanceof AccessDeniedException) {
            return ApiResponse.error("Not authorized");
        }
        log.warn("Unexpected WebSocket error", ex);
        return ApiResponse.error("Invalid location update");
    }

    private static UserPrincipal principalOf(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth && auth.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        throw new AccessDeniedException("Not authenticated");
    }
}

package com.rideshare.booking.controller;

import com.rideshare.booking.dto.BookingResponse;
import com.rideshare.booking.dto.CreateBookingRequest;
import com.rideshare.booking.service.BookingService;
import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.response.ApiResponse;
import com.rideshare.common.response.PageResponse;
import com.rideshare.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "5. Bookings", description = "Request seats (passenger) and accept/reject requests (driver). "
        + "Bookings start PENDING; seats are allocated only on ACCEPT.")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Request seats on a ride",
            description = "422 when booking your own ride or requesting more seats than available; "
                    + "409 if the ride is not open or you already hold an active booking for it.")
    public ResponseEntity<ApiResponse<BookingResponse>> create(@AuthenticationPrincipal UserPrincipal principal,
                                                               @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking requested", bookingService.create(principal.id(), request)));
    }

    @GetMapping("/my-bookings")
    @Operation(summary = "Bookings I made as a passenger", description = "Optional status filter.")
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> myBookings(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) BookingStatus status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Bookings retrieved",
                bookingService.myBookings(principal.id(), status, pageable)));
    }

    @GetMapping("/received-requests")
    @Operation(summary = "Booking requests received on my rides (driver view)",
            description = "Optional filters: rideId, status (e.g. PENDING).")
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> received(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID rideId,
            @RequestParam(required = false) BookingStatus status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Booking requests retrieved",
                bookingService.receivedRequests(principal.id(), rideId, status, pageable)));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get a booking", description = "Passenger, ride driver or admin only.")
    public ResponseEntity<ApiResponse<BookingResponse>> get(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.success("Booking retrieved", bookingService.get(principal, bookingId)));
    }

    @PatchMapping("/{bookingId}/accept")
    @Operation(summary = "Accept a pending request (driver)", description = "Allocates seats atomically. "
            + "422 if not enough seats remain, 409 if the booking is not PENDING.")
    public ResponseEntity<ApiResponse<BookingResponse>> accept(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.success("Booking accepted", bookingService.accept(principal.id(), bookingId)));
    }

    @PatchMapping("/{bookingId}/reject")
    @Operation(summary = "Reject a pending request (driver)")
    public ResponseEntity<ApiResponse<BookingResponse>> reject(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.success("Booking rejected", bookingService.reject(principal.id(), bookingId)));
    }

    @PatchMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel my booking (passenger)", description = "Allowed while PENDING or ACCEPTED and the "
            + "ride has not started. Accepted seats are released and successful payments refunded.")
    public ResponseEntity<ApiResponse<BookingResponse>> cancel(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled", bookingService.cancel(principal.id(), bookingId)));
    }
}

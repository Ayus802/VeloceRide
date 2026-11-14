package com.rideshare.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Envelope used by every REST endpoint so the frontend can handle responses uniformly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard response envelope")
public record ApiResponse<T>(
        @Schema(example = "true") boolean success,
        @Schema(example = "Ride published successfully") String message,
        T data,
        List<ApiError> errors,
        @Schema(example = "2026-11-15T10:30:00") LocalDateTime timestamp) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, LocalDateTime.now());
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(true, message, null, null, LocalDateTime.now());
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null, List.of(), LocalDateTime.now());
    }

    public static ApiResponse<Void> error(String message, List<ApiError> errors) {
        return new ApiResponse<>(false, message, null, errors == null ? List.of() : errors, LocalDateTime.now());
    }
}

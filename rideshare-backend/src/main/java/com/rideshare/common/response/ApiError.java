package com.rideshare.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A single validation or business error")
public record ApiError(
        @Schema(example = "email") String field,
        @Schema(example = "must be a well-formed email address") String message) {
}

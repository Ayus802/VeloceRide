package com.rideshare.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModerateReviewRequest(
        @Schema(description = "true hides the review from listings and ratings", example = "true") @NotNull Boolean hidden,
        @Schema(example = "Abusive language") @Size(max = 500) String note) {
}

package com.rideshare.user.dto;

import com.rideshare.common.enums.ActiveMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Switch the UI mode. This is a preference only; it does not change what the account may do.")
public record ActiveModeRequest(@Schema(example = "RIDER") @NotNull ActiveMode activeMode) {
}

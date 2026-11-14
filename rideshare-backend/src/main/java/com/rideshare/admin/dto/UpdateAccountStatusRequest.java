package com.rideshare.admin.dto;

import com.rideshare.common.enums.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(@Schema(example = "DEACTIVATED") @NotNull AccountStatus accountStatus) {
}

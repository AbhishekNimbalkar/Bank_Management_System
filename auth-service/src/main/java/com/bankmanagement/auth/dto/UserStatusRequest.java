package com.bankmanagement.auth.dto;

import com.bankmanagement.auth.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull UserStatus status) {
}

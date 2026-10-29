package com.bankmanagement.auth.dto;

import jakarta.validation.constraints.NotNull;

public record UserLockRequest(@NotNull Boolean locked) {
}

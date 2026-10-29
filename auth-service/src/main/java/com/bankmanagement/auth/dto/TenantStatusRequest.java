package com.bankmanagement.auth.dto;

import com.bankmanagement.auth.domain.TenantAccess;
import jakarta.validation.constraints.NotNull;

public record TenantStatusRequest(@NotNull TenantAccess.Status status) {
}

package com.bankmanagement.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Approve or reject a pending request, with an optional note for the employee. */
public record DecisionRequest(@NotNull Boolean approved,
                              @Size(max = 200) @Pattern(regexp = "^[^<>]*$") String note) {
}

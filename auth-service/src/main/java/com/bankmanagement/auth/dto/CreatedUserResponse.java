package com.bankmanagement.auth.dto;

import java.util.UUID;

/** The temporary password is returned exactly once and never stored in readable form. */
public record CreatedUserResponse(UUID userId, String username, String temporaryPassword) {
}

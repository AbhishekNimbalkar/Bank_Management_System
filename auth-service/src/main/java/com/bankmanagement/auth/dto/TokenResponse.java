package com.bankmanagement.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Refresh token is omitted for impersonation sessions, which cannot be extended. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserProfile user) {
}

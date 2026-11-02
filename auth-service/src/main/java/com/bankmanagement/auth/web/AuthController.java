package com.bankmanagement.auth.web;

import com.bankmanagement.auth.dto.ChangePasswordRequest;
import com.bankmanagement.auth.dto.LoginRequest;
import com.bankmanagement.auth.dto.RefreshRequest;
import com.bankmanagement.auth.dto.TokenResponse;
import com.bankmanagement.auth.dto.UserProfile;
import com.bankmanagement.auth.service.AuthService;
import com.bankmanagement.common.api.ApiResponse;
import com.bankmanagement.common.web.ClientIp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Authentication", description = "Sign-in, token refresh, sign-out and password change")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Sign in and receive an access token + refresh token")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest http) {
        TokenResponse tokens = authService.login(request, ClientIp.resolve(http), http.getHeader(HttpHeaders.USER_AGENT));
        return noStore(ApiResponse.ok("Signed in successfully.", tokens));
    }

    @Operation(summary = "Exchange a refresh token for a new token pair (rotation)")
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request,
                                                              HttpServletRequest http) {
        TokenResponse tokens = authService.refresh(request.refreshToken(), ClientIp.resolve(http),
                http.getHeader(HttpHeaders.USER_AGENT));
        return noStore(ApiResponse.ok(tokens));
    }

    @Operation(summary = "Sign out: revokes the refresh token and every token issued from the same sign-in")
    @SecurityRequirements
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.message("Signed out.");
    }

    @Operation(summary = "Current user profile and effective permissions")
    @GetMapping("/me")
    public ApiResponse<UserProfile> me() {
        return ApiResponse.ok(authService.me());
    }

    @Operation(summary = "Change own password; all other sessions are signed out")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<TokenResponse>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                                     HttpServletRequest http) {
        TokenResponse tokens = authService.changePassword(request, ClientIp.resolve(http),
                http.getHeader(HttpHeaders.USER_AGENT));
        return noStore(ApiResponse.ok("Password changed.", tokens));
    }

    @Operation(summary = "Platform owner: open an audited, short-lived support session as a user")
    @PostMapping("/impersonate/{userId}")
    public ResponseEntity<ApiResponse<TokenResponse>> impersonate(@PathVariable UUID userId) {
        return noStore(ApiResponse.ok("Support session opened.", authService.impersonate(userId)));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(body);
    }
}

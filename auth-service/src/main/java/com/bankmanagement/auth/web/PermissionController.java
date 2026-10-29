package com.bankmanagement.auth.web;

import com.bankmanagement.auth.dto.PermissionUpdateRequest;
import com.bankmanagement.auth.service.PermissionService;
import com.bankmanagement.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Permissions", description = "Role x module CRUD matrix per organization")
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "Permission matrix of the organization; pass custom role codes to include them")
    @PreAuthorize("hasAuthority('permissions:R')")
    @GetMapping
    public ApiResponse<Map<String, Map<String, String>>> matrix(
            @RequestParam(name = "customRoles", required = false) List<String> customRoles) {
        return ApiResponse.ok(permissionService.matrix(customRoles == null ? List.of() : customRoles));
    }

    @Operation(summary = "Grant or revoke one action for a role on a module (applies at next token refresh)")
    @PreAuthorize("hasAuthority('permissions:U')")
    @PutMapping
    public ApiResponse<Map<String, Map<String, String>>> update(@Valid @RequestBody PermissionUpdateRequest request) {
        return ApiResponse.ok("Permission updated.", permissionService.update(request));
    }

    @Operation(summary = "Restore the default matrix")
    @PreAuthorize("hasAuthority('permissions:U')")
    @PostMapping("/reset")
    public ApiResponse<Map<String, Map<String, String>>> reset() {
        return ApiResponse.ok("Permissions restored to defaults.", permissionService.reset());
    }
}

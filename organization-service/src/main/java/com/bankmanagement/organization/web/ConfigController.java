package com.bankmanagement.organization.web;

import com.bankmanagement.common.api.ApiResponse;
import com.bankmanagement.organization.dto.ConfigDto;
import com.bankmanagement.organization.service.CustomRoleService;
import com.bankmanagement.organization.service.DesignationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Configuration", description = "Designations and custom roles")
@RestController
@RequiredArgsConstructor
public class ConfigController {

    private final DesignationService designationService;
    private final CustomRoleService customRoleService;

    @Operation(summary = "List designations")
    @PreAuthorize("hasAuthority('config:R') or hasAuthority('employees:C')")
    @GetMapping("/api/v1/designations")
    public ApiResponse<List<ConfigDto.DesignationResponse>> designations() {
        return ApiResponse.ok(designationService.list());
    }

    @Operation(summary = "Create a designation")
    @PreAuthorize("hasAuthority('config:C')")
    @PostMapping("/api/v1/designations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConfigDto.DesignationResponse> createDesignation(
            @Valid @RequestBody ConfigDto.DesignationRequest request) {
        return ApiResponse.ok("Designation created.", designationService.create(request));
    }

    @Operation(summary = "Update a designation")
    @PreAuthorize("hasAuthority('config:U')")
    @PutMapping("/api/v1/designations/{id}")
    public ApiResponse<ConfigDto.DesignationResponse> updateDesignation(
            @PathVariable UUID id, @Valid @RequestBody ConfigDto.DesignationRequest request) {
        return ApiResponse.ok("Designation updated.", designationService.update(id, request));
    }

    @Operation(summary = "Delete an unused designation")
    @PreAuthorize("hasAuthority('config:D')")
    @DeleteMapping("/api/v1/designations/{id}")
    public ApiResponse<Void> deleteDesignation(@PathVariable UUID id) {
        designationService.delete(id);
        return ApiResponse.message("Designation deleted.");
    }

    @Operation(summary = "List built-in and custom roles")
    @PreAuthorize("hasAuthority('config:R') or hasAuthority('employees:C')")
    @GetMapping("/api/v1/roles")
    public ApiResponse<List<ConfigDto.RoleResponse>> roles() {
        return ApiResponse.ok(customRoleService.list());
    }

    @Operation(summary = "Create a custom role")
    @PreAuthorize("hasAuthority('config:C')")
    @PostMapping("/api/v1/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConfigDto.RoleResponse> createRole(@Valid @RequestBody ConfigDto.RoleRequest request) {
        return ApiResponse.ok("Role created.", customRoleService.create(request));
    }

    @Operation(summary = "Update a custom role")
    @PreAuthorize("hasAuthority('config:U')")
    @PutMapping("/api/v1/roles/{id}")
    public ApiResponse<ConfigDto.RoleResponse> updateRole(@PathVariable UUID id,
                                                          @Valid @RequestBody ConfigDto.RoleRequest request) {
        return ApiResponse.ok("Role updated.", customRoleService.update(id, request));
    }

    @Operation(summary = "Delete an unused custom role")
    @PreAuthorize("hasAuthority('config:D')")
    @DeleteMapping("/api/v1/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable UUID id) {
        customRoleService.delete(id);
        return ApiResponse.message("Role deleted.");
    }
}

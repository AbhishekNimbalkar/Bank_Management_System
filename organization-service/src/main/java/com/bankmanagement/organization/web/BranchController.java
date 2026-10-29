package com.bankmanagement.organization.web;

import com.bankmanagement.common.api.ApiResponse;
import com.bankmanagement.organization.dto.BranchDto;
import com.bankmanagement.organization.service.BranchService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Branches", description = "Physical branches and their attendance geofences")
@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @Operation(summary = "List branches, optionally of one bank")
    @PreAuthorize("hasAuthority('branches:R')")
    @GetMapping
    public ApiResponse<List<BranchDto.Response>> list(@RequestParam(required = false) UUID bankId) {
        return ApiResponse.ok(branchService.list(bankId));
    }

    @Operation(summary = "Get a branch")
    @PreAuthorize("hasAuthority('branches:R')")
    @GetMapping("/{id}")
    public ApiResponse<BranchDto.Response> get(@PathVariable UUID id) {
        return ApiResponse.ok(branchService.get(id));
    }

    @Operation(summary = "Geofence of a branch (own branch for any user)")
    @GetMapping("/{id}/geofence")
    public ApiResponse<BranchDto.Geofence> geofence(@PathVariable UUID id) {
        return ApiResponse.ok(branchService.geofence(id));
    }

    @Operation(summary = "Register a branch with its geofence")
    @PreAuthorize("hasAuthority('branches:C')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BranchDto.Response> create(@Valid @RequestBody BranchDto.Request request) {
        return ApiResponse.ok("Branch registered.", branchService.create(request));
    }

    @Operation(summary = "Update a branch and its geofence")
    @PreAuthorize("hasAuthority('branches:U')")
    @PutMapping("/{id}")
    public ApiResponse<BranchDto.Response> update(@PathVariable UUID id, @Valid @RequestBody BranchDto.Request request) {
        return ApiResponse.ok("Branch updated.", branchService.update(id, request));
    }

    @Operation(summary = "Delete a branch without staff (headquarters cannot be deleted)")
    @PreAuthorize("hasAuthority('branches:D')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        branchService.delete(id);
        return ApiResponse.message("Branch deleted.");
    }
}

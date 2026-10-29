package com.bankmanagement.organization.web;

import com.bankmanagement.common.api.ApiResponse;
import com.bankmanagement.organization.dto.BankDto;
import com.bankmanagement.organization.service.BankService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Banks", description = "Banking entities of the organization")
@RestController
@RequestMapping("/api/v1/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;

    @Operation(summary = "List banks")
    @PreAuthorize("hasAuthority('banks:R')")
    @GetMapping
    public ApiResponse<List<BankDto.Response>> list() {
        return ApiResponse.ok(bankService.list());
    }

    @Operation(summary = "Get a bank")
    @PreAuthorize("hasAuthority('banks:R')")
    @GetMapping("/{id}")
    public ApiResponse<BankDto.Response> get(@PathVariable UUID id) {
        return ApiResponse.ok(bankService.get(id));
    }

    @Operation(summary = "Register a bank; its headquarters branch is created automatically")
    @PreAuthorize("hasAuthority('banks:C')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BankDto.Response> create(@Valid @RequestBody BankDto.Request request) {
        return ApiResponse.ok("Bank registered.", bankService.create(request));
    }

    @Operation(summary = "Update a bank")
    @PreAuthorize("hasAuthority('banks:U')")
    @PutMapping("/{id}")
    public ApiResponse<BankDto.Response> update(@PathVariable UUID id, @Valid @RequestBody BankDto.Request request) {
        return ApiResponse.ok("Bank updated.", bankService.update(id, request));
    }

    @Operation(summary = "Delete a bank without branches")
    @PreAuthorize("hasAuthority('banks:D')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        bankService.delete(id);
        return ApiResponse.message("Bank deleted.");
    }
}

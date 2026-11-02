package com.bankmanagement.incentive.web;

import com.bankmanagement.common.api.ApiResponse;
import com.bankmanagement.common.api.PageResponse;
import com.bankmanagement.incentive.domain.Enums;
import com.bankmanagement.incentive.dto.IncentiveDto;
import com.bankmanagement.incentive.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Customers & leads", description = "Loan files and deposit accounts; conversion is maker-checker")
@Validated
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @Operation(summary = "Search customer records in scope")
    @PreAuthorize("hasAuthority('customers:R')")
    @GetMapping
    public ApiResponse<PageResponse<IncentiveDto.CustomerResponse>> list(
            @RequestParam(required = false) Enums.CustomerStatus status,
            @RequestParam(required = false) Enums.ProductType productType,
            @RequestParam(required = false) @Pattern(regexp = IncentiveDto.MONTH) String month,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(customerService.list(status, productType, month, search, page, size));
    }

    @Operation(summary = "Record a customer lead (managers may credit a junior via ownerEmployeeId)")
    @PreAuthorize("hasAuthority('customers:C')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IncentiveDto.CustomerResponse> create(@Valid @RequestBody IncentiveDto.CustomerRequest request) {
        return ApiResponse.ok("Lead recorded. A manager must approve the conversion.", customerService.create(request));
    }

    @Operation(summary = "Edit an open lead (owner or maker only)")
    @PreAuthorize("hasAuthority('customers:U')")
    @PutMapping("/{id}")
    public ApiResponse<IncentiveDto.CustomerResponse> update(@PathVariable UUID id,
                                                             @Valid @RequestBody IncentiveDto.CustomerUpdate request) {
        return ApiResponse.ok("Lead updated.", customerService.update(id, request));
    }

    @Operation(summary = "Checker: approve (converted) or reject a lead")
    @PreAuthorize("hasAuthority('customers:U')")
    @PatchMapping("/{id}/decision")
    public ApiResponse<IncentiveDto.CustomerResponse> decide(@PathVariable UUID id,
                                                             @Valid @RequestBody IncentiveDto.Decision request) {
        return ApiResponse.ok("Decision recorded.", customerService.decide(id, request));
    }

    @Operation(summary = "Delete an open lead")
    @PreAuthorize("hasAuthority('customers:D')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ApiResponse.message("Lead deleted.");
    }
}

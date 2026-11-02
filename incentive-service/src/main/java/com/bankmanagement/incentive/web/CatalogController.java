package com.bankmanagement.incentive.web;

import com.bankmanagement.common.api.ApiResponse;
import com.bankmanagement.incentive.dto.IncentiveDto;
import com.bankmanagement.incentive.service.PlanService;
import com.bankmanagement.incentive.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Products & incentive plans", description = "Loan/deposit catalogue and monthly incentive schemes")
@Validated
@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final ProductService productService;
    private final PlanService planService;

    @Operation(summary = "Loan and deposit products")
    @PreAuthorize("hasAuthority('products:R')")
    @GetMapping("/api/v1/products")
    public ApiResponse<List<IncentiveDto.ProductResponse>> products() {
        return ApiResponse.ok(productService.list());
    }

    @Operation(summary = "Add a product")
    @PreAuthorize("hasAuthority('products:C')")
    @PostMapping("/api/v1/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IncentiveDto.ProductResponse> createProduct(@Valid @RequestBody IncentiveDto.ProductRequest request) {
        return ApiResponse.ok("Product created.", productService.create(request));
    }

    @Operation(summary = "Update a product (rate, tenure, availability)")
    @PreAuthorize("hasAuthority('products:U')")
    @PutMapping("/api/v1/products/{id}")
    public ApiResponse<IncentiveDto.ProductResponse> updateProduct(@PathVariable UUID id,
                                                                  @Valid @RequestBody IncentiveDto.ProductRequest request) {
        return ApiResponse.ok("Product updated.", productService.update(id, request));
    }

    @Operation(summary = "Incentive plans, optionally of one month (YYYY-MM)")
    @PreAuthorize("hasAuthority('plans:R')")
    @GetMapping("/api/v1/incentive-plans")
    public ApiResponse<List<IncentiveDto.PlanResponse>> plans(
            @RequestParam(required = false) @Pattern(regexp = IncentiveDto.MONTH) String month) {
        return ApiResponse.ok(planService.list(month));
    }

    @Operation(summary = "Create an incentive plan for the current or a future month")
    @PreAuthorize("hasAuthority('plans:C')")
    @PostMapping("/api/v1/incentive-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IncentiveDto.PlanResponse> createPlan(@Valid @RequestBody IncentiveDto.PlanRequest request) {
        return ApiResponse.ok("Incentive plan created.", planService.create(request));
    }

    @Operation(summary = "Update a plan (past months are frozen)")
    @PreAuthorize("hasAuthority('plans:U')")
    @PutMapping("/api/v1/incentive-plans/{id}")
    public ApiResponse<IncentiveDto.PlanResponse> updatePlan(@PathVariable UUID id,
                                                            @Valid @RequestBody IncentiveDto.PlanRequest request) {
        return ApiResponse.ok("Incentive plan updated.", planService.update(id, request));
    }
}

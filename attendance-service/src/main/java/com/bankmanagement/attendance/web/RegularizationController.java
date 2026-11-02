package com.bankmanagement.attendance.web;

import com.bankmanagement.attendance.dto.AttendanceDto;
import com.bankmanagement.attendance.dto.DecisionRequest;
import com.bankmanagement.attendance.service.RegularizationService;
import com.bankmanagement.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Attendance corrections", description = "Requests for missed punches or GPS drift")
@RestController
@RequestMapping("/api/v1/regularizations")
@RequiredArgsConstructor
public class RegularizationController {

    private final RegularizationService regularizationService;

    @Operation(summary = "List correction requests in scope")
    @PreAuthorize("hasAuthority('regs:R')")
    @GetMapping
    public ApiResponse<List<AttendanceDto.CorrectionResponse>> list() {
        return ApiResponse.ok(regularizationService.list());
    }

    @Operation(summary = "Request a correction for one of the last 30 days")
    @PreAuthorize("hasAuthority('regs:C')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AttendanceDto.CorrectionResponse> create(@Valid @RequestBody AttendanceDto.CorrectionRequest request) {
        return ApiResponse.ok("Correction requested.", regularizationService.create(request));
    }

    @Operation(summary = "Approve or reject a correction (senior in scope only)")
    @PreAuthorize("hasAuthority('regs:U')")
    @PatchMapping("/{id}/decision")
    public ApiResponse<AttendanceDto.CorrectionResponse> decide(@PathVariable UUID id,
                                                                @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.ok("Decision recorded.", regularizationService.decide(id, request));
    }

    @Operation(summary = "Cancel my pending correction")
    @PreAuthorize("hasAuthority('regs:D')")
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable UUID id) {
        regularizationService.cancel(id);
        return ApiResponse.message("Correction cancelled.");
    }
}

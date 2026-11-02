package com.bankmanagement.incentive.web;

import com.bankmanagement.common.api.ApiResponse;
import com.bankmanagement.incentive.dto.IncentiveDto;
import com.bankmanagement.incentive.service.IncentiveDashboardService;
import com.bankmanagement.incentive.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Leaderboard", description = "Monthly performance ranking and incentive statements")
@Validated
@RestController
@RequestMapping("/api/v1/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final IncentiveDashboardService dashboardService;

    @Operation(summary = "Ranking for a month; incentive amounts shown only for employees in your scope")
    @PreAuthorize("hasAuthority('leaderboard:R')")
    @GetMapping
    public ApiResponse<IncentiveDto.Leaderboard> leaderboard(
            @RequestParam @Pattern(regexp = IncentiveDto.MONTH) String month,
            @RequestParam(required = false) UUID planId) {
        return ApiResponse.ok(leaderboardService.leaderboard(month, planId));
    }

    @Operation(summary = "Dashboard: my progress on this month's plan and leads awaiting my approval")
    @PreAuthorize("hasAuthority('dashboard:R')")
    @GetMapping("/dashboard")
    public ApiResponse<IncentiveDashboardService.Summary> dashboard() {
        return ApiResponse.ok(dashboardService.summary());
    }

    @Operation(summary = "Incentive statement CSV for payroll (rows in your scope)")
    @PreAuthorize("hasAuthority('leaderboard:R')")
    @GetMapping("/statement")
    public ResponseEntity<byte[]> statement(@RequestParam @Pattern(regexp = IncentiveDto.MONTH) String month,
                                            @RequestParam(required = false) UUID planId) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("incentive-statement-" + month + ".csv").build().toString())
                .body(leaderboardService.statementCsv(month, planId));
    }
}

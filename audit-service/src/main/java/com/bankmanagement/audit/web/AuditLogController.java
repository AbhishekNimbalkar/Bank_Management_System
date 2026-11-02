package com.bankmanagement.audit.web;

import com.bankmanagement.audit.dto.AuditDto;
import com.bankmanagement.audit.service.AuditQueryService;
import com.bankmanagement.common.api.ApiResponse;
import com.bankmanagement.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Audit logs", description = "Tamper-evident, hash-chained audit trail")
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditQueryService queryService;

    @Operation(summary = "Search the organization's audit trail (newest first)")
    @PreAuthorize("hasAuthority('logs:R')")
    @GetMapping
    public ApiResponse<PageResponse<AuditDto.Entry>> search(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(queryService.search(new AuditDto.Filter(module, search, from, to), page, size));
    }

    @Operation(summary = "Verify the hash chain; reports the first altered entry if any")
    @PreAuthorize("hasAuthority('logs:R')")
    @GetMapping("/verify")
    public ApiResponse<AuditDto.Verification> verify() {
        return ApiResponse.ok(queryService.verify());
    }

    @Operation(summary = "Export matching entries as CSV (max 50,000 rows)")
    @PreAuthorize("hasAuthority('logs:R')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("audit-log.csv").build().toString())
                .body(queryService.export(new AuditDto.Filter(module, search, from, to)));
    }
}

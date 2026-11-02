package com.bankmanagement.organization.web;

import com.bankmanagement.common.api.ApiResponse;
import com.bankmanagement.organization.dto.AnnouncementDto;
import com.bankmanagement.organization.service.AnnouncementService;
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

@Tag(name = "Announcements", description = "Organization and branch notices")
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "Current announcements for me (organization-wide + my branch), pinned first")
    @PreAuthorize("hasAuthority('announcements:R')")
    @GetMapping
    public ApiResponse<List<AnnouncementDto.Response>> list() {
        return ApiResponse.ok(announcementService.list());
    }

    @Operation(summary = "Post an announcement")
    @PreAuthorize("hasAuthority('announcements:C')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AnnouncementDto.Response> create(@Valid @RequestBody AnnouncementDto.Request request) {
        return ApiResponse.ok("Announcement posted.", announcementService.create(request));
    }

    @Operation(summary = "Edit an announcement")
    @PreAuthorize("hasAuthority('announcements:U')")
    @PutMapping("/{id}")
    public ApiResponse<AnnouncementDto.Response> update(@PathVariable UUID id,
                                                        @Valid @RequestBody AnnouncementDto.Request request) {
        return ApiResponse.ok("Announcement updated.", announcementService.update(id, request));
    }

    @Operation(summary = "Delete an announcement")
    @PreAuthorize("hasAuthority('announcements:D')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        announcementService.delete(id);
        return ApiResponse.message("Announcement deleted.");
    }
}

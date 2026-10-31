package com.bankmanagement.attendance.web;

import com.bankmanagement.attendance.dto.DecisionRequest;
import com.bankmanagement.attendance.dto.TimesheetDto;
import com.bankmanagement.attendance.service.TimesheetService;
import com.bankmanagement.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Timesheets", description = "Daily work logs and their approval")
@RestController
@RequestMapping("/api/v1/timesheets")
@RequiredArgsConstructor
public class TimesheetController {

    private final TimesheetService timesheetService;

    @Operation(summary = "List timesheet entries in scope")
    @PreAuthorize("hasAuthority('timesheets:R')")
    @GetMapping
    public ApiResponse<List<TimesheetDto.Response>> list() {
        return ApiResponse.ok(timesheetService.list());
    }

    @Operation(summary = "Log work for one of the last 14 days")
    @PreAuthorize("hasAuthority('timesheets:C')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TimesheetDto.Response> create(@Valid @RequestBody TimesheetDto.Request request) {
        return ApiResponse.ok("Timesheet logged.", timesheetService.create(request));
    }

    @Operation(summary = "Edit my entry while it awaits approval")
    @PreAuthorize("hasAuthority('timesheets:U')")
    @PutMapping("/{id}")
    public ApiResponse<TimesheetDto.Response> update(@PathVariable UUID id, @Valid @RequestBody TimesheetDto.Request request) {
        return ApiResponse.ok("Timesheet updated.", timesheetService.update(id, request));
    }

    @Operation(summary = "Delete my entry while it awaits approval")
    @PreAuthorize("hasAuthority('timesheets:D')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        timesheetService.delete(id);
        return ApiResponse.message("Timesheet deleted.");
    }

    @Operation(summary = "Approve or reject an entry (senior in scope only)")
    @PreAuthorize("hasAuthority('timesheets:U')")
    @PatchMapping("/{id}/decision")
    public ApiResponse<TimesheetDto.Response> decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.ok("Decision recorded.", timesheetService.decide(id, request));
    }

    @Operation(summary = "Comments on an entry (visible to the employee, their managers and the Super Admin)")
    @PreAuthorize("hasAuthority('timesheets:R')")
    @GetMapping("/{id}/comments")
    public ApiResponse<List<TimesheetDto.CommentResponse>> comments(@PathVariable UUID id) {
        return ApiResponse.ok(timesheetService.comments(id));
    }

    @Operation(summary = "Comment on an employee's entry (manager senior to the employee, in scope)")
    @PreAuthorize("hasAuthority('timesheets:U')")
    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TimesheetDto.CommentResponse> addComment(@PathVariable UUID id,
                                                                @Valid @RequestBody TimesheetDto.CommentRequest request) {
        return ApiResponse.ok("Comment added.", timesheetService.addComment(id, request));
    }

    @Operation(summary = "Delete my own comment")
    @PreAuthorize("hasAuthority('timesheets:U')")
    @DeleteMapping("/{id}/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@PathVariable UUID id, @PathVariable UUID commentId) {
        timesheetService.deleteComment(id, commentId);
        return ApiResponse.message("Comment deleted.");
    }
}

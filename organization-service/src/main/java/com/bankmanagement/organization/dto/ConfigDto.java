package com.bankmanagement.organization.dto;

import com.bankmanagement.organization.domain.CustomRole;
import com.bankmanagement.organization.domain.Designation;
import com.bankmanagement.organization.domain.RecordStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Organization configuration: designations and roles. */
public final class ConfigDto {

    private ConfigDto() {
    }

    public record DesignationRequest(
            @NotBlank @Size(min = 2, max = 60) @Pattern(regexp = Patterns.SAFE_TEXT) String title,
            @Size(max = 60) @Pattern(regexp = Patterns.SAFE_TEXT) String department,
            @Size(max = 40) @Pattern(regexp = Patterns.SAFE_TEXT) String grade,
            @Size(max = 240) @Pattern(regexp = Patterns.SAFE_TEXT) String description,
            RecordStatus status) {
    }

    public record DesignationResponse(UUID id, String title, String department, String grade, String description,
                                      RecordStatus status, long staffCount) {

        public static DesignationResponse from(Designation d, long staffCount) {
            return new DesignationResponse(d.getId(), d.getTitle(), d.getDepartment(), d.getGrade(),
                    d.getDescription(), d.getStatus(), staffCount);
        }
    }

    public record RoleRequest(
            @NotBlank @Size(min = 2, max = 60) @Pattern(regexp = Patterns.SAFE_TEXT) String name,
            @Pattern(regexp = Patterns.ROLE_CODE, message = "2-10 upper-case letters/digits") String code,
            @Size(max = 60) @Pattern(regexp = Patterns.SAFE_TEXT) String category,
            @Size(max = 240) @Pattern(regexp = Patterns.SAFE_TEXT) String description,
            RecordStatus status) {
    }

    /** Built-in roles are listed with {@code system = true} and cannot be changed or deleted. */
    public record RoleResponse(UUID id, String code, String name, String category, String description,
                               boolean system, RecordStatus status, long staffCount) {

        public static RoleResponse from(CustomRole r, long staffCount) {
            return new RoleResponse(r.getId(), r.getCode(), r.getName(), r.getCategory(), r.getDescription(),
                    false, r.getStatus(), staffCount);
        }
    }
}

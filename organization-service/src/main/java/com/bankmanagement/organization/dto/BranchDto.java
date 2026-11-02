package com.bankmanagement.organization.dto;

import com.bankmanagement.organization.domain.Branch;
import com.bankmanagement.organization.domain.RecordStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class BranchDto {

    private BranchDto() {
    }

    public record Request(
            @NotNull UUID bankId,
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = Patterns.SAFE_TEXT) String name,
            @NotBlank @Pattern(regexp = Patterns.IFSC, message = "must look like ABCD0123456") String ifsc,
            @NotNull @Valid LocationDto location,
            @NotNull @Min(50) @Max(1000) Integer radiusMeters,
            RecordStatus status) {
    }

    public record Response(UUID branchId, UUID bankId, String bankName, String name, String ifsc, LocationDto location,
                           String address, int radiusMeters, boolean headquarters, RecordStatus status,
                           long staffCount) {

        public static Response from(Branch b, String bankName, long staffCount) {
            return new Response(b.getId(), b.getBankId(), bankName, b.getName(), b.getIfsc(),
                    LocationDto.from(b.getLocation()), b.getLocation().getAddress(), b.getRadiusMeters(),
                    b.isHeadquarters(), b.getStatus(), staffCount);
        }
    }

    /** Minimal view used by attendance-service for geofence checks. */
    public record Geofence(UUID branchId, String name, double latitude, double longitude, int radiusMeters,
                           boolean active) {

        public static Geofence from(Branch b) {
            return new Geofence(b.getId(), b.getName(), b.getLocation().getLatitude(), b.getLocation().getLongitude(),
                    b.getRadiusMeters(), b.getStatus() == RecordStatus.ACTIVE);
        }
    }
}

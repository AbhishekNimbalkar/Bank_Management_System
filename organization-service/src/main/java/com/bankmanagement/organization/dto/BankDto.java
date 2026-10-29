package com.bankmanagement.organization.dto;

import com.bankmanagement.organization.domain.Bank;
import com.bankmanagement.organization.domain.RecordStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class BankDto {

    private BankDto() {
    }

    public record Request(
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = Patterns.SAFE_TEXT) String name,
            @Pattern(regexp = "^[A-Z0-9]{2,6}$", message = "2-6 upper-case letters or digits") String code,
            @NotNull @Valid LocationDto location,
            RecordStatus status) {
    }

    public record Response(UUID id, String name, String code, LocationDto location, String address,
                           RecordStatus status, long branchCount) {

        public static Response from(Bank b, long branchCount) {
            return new Response(b.getId(), b.getName(), b.getCode(), LocationDto.from(b.getLocation()),
                    b.getLocation().getAddress(), b.getStatus(), branchCount);
        }
    }
}

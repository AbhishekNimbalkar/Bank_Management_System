package com.bankmanagement.organization.dto;

import com.bankmanagement.organization.domain.Employee;
import com.bankmanagement.organization.domain.RecordStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public final class EmployeeDto {

    private EmployeeDto() {
    }

    public record CreateRequest(
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = Patterns.SAFE_TEXT) String name,
            @Pattern(regexp = Patterns.USERNAME, message = "3-32 lower-case letters, numbers, . _ -") String username,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Pattern(regexp = Patterns.MOBILE, message = "must be a valid 10-digit mobile number") String phone,
            @NotBlank @Pattern(regexp = Patterns.ROLE_CODE) String role,
            @NotBlank @Size(max = 60) @Pattern(regexp = Patterns.SAFE_TEXT) String designation,
            @NotNull UUID branchId,
            @NotBlank @Size(max = 240) @Pattern(regexp = Patterns.SAFE_TEXT) String address,
            @Valid SalaryAccount salaryAccount) {
    }

    public record UpdateRequest(
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = Patterns.SAFE_TEXT) String name,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Pattern(regexp = Patterns.MOBILE, message = "must be a valid 10-digit mobile number") String phone,
            @NotBlank @Size(max = 60) @Pattern(regexp = Patterns.SAFE_TEXT) String designation,
            @NotBlank @Size(max = 240) @Pattern(regexp = Patterns.SAFE_TEXT) String address,
            @Valid SalaryAccount salaryAccount) {
    }

    public record SalaryAccount(
            @NotBlank @Size(max = 60) @Pattern(regexp = Patterns.SAFE_TEXT) String bankName,
            @NotBlank @Size(max = 120) @Pattern(regexp = Patterns.SAFE_TEXT) String branchAddress,
            @NotBlank @Pattern(regexp = Patterns.ACCOUNT_NUMBER, message = "must be 9-18 digits") String accountNumber,
            @NotBlank String confirmAccountNumber,
            @NotBlank @Pattern(regexp = Patterns.IFSC, message = "must look like ABCD0123456") String ifsc) {

        @Override
        public String toString() {
            return "SalaryAccount[bankName=" + bankName + ", account=***]";
        }
    }

    public record StatusRequest(@NotNull RecordStatus status) {
    }

    /** Transfer to another branch and/or change of role and designation. */
    public record AssignmentRequest(
            @NotNull UUID branchId,
            @NotBlank @Pattern(regexp = Patterns.ROLE_CODE) String role,
            @NotBlank @Size(max = 60) @Pattern(regexp = Patterns.SAFE_TEXT) String designation,
            @Size(max = 200) @Pattern(regexp = Patterns.SAFE_TEXT) String reason) {
    }

    public record LockRequest(@NotNull Boolean locked) {
    }

    /** Account number is masked unless the viewer is HR/SA/platform owner or the employee themself. */
    public record Response(UUID employeeId, UUID userId, String code, String name, String username, String email,
                           String phone, String role, String designation, UUID bankId, String bankName,
                           UUID branchId, String branchName, boolean headquarters, String address,
                           LocalDate joinedOn, RecordStatus status, String salaryBankName,
                           String salaryBranchAddress, String salaryAccountNumber, String salaryIfsc) {

        public static Response from(Employee e, String bankName, String branchName, boolean hq, boolean revealAccount) {
            String account = e.getSalaryAccountLast4() == null ? null
                    : revealAccount ? e.getSalaryAccountNumber() : "XXXXXX" + e.getSalaryAccountLast4();
            return new Response(e.getId(), e.getUserId(), e.getCode(), e.getName(), e.getUsername(), e.getEmail(),
                    e.getPhone(), e.getRole(), e.getDesignation(), e.getBankId(), bankName, e.getBranchId(),
                    branchName, hq, e.getAddress(), e.getJoinedOn(), e.getStatus(), e.getSalaryBankName(),
                    e.getSalaryBranchAddress(), account, e.getSalaryIfsc());
        }
    }

    /** Returned once on creation; the temporary password is never retrievable again. */
    public record Created(Response employee, String username, String temporaryPassword) {
    }

    public record TemporaryPassword(String username, String temporaryPassword) {
    }
}

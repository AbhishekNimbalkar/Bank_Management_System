package com.bankmanagement.organization.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import com.bankmanagement.organization.crypto.EncryptedStringConverter;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.UUID;

/** Employee profile. Credentials live in auth-service and are linked via {@code userId}. */
@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "employees", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_code", "code"}),
        @UniqueConstraint(columnNames = {"tenant_code", "username"})})
@AttributeOverride(name = "id", column = @Column(name = "employee_id"))
public class Employee extends TenantScopedEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(name = "code_number", nullable = false)
    private int codeNumber;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false, length = 10)
    private String phone;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(nullable = false, length = 60)
    private String designation;

    // Null only for the Super Admin created at registration, before the client has added any branch.
    @Column(name = "bank_id")
    private UUID bankId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(length = 240)
    private String address;

    @Column(name = "joined_on", nullable = false)
    private LocalDate joinedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;

    @Column(name = "salary_bank_name", length = 60)
    private String salaryBankName;

    @Column(name = "salary_branch_address", length = 120)
    private String salaryBranchAddress;

    /** Encrypted at rest (AES-256-GCM). */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "salary_account_number", length = 120)
    private String salaryAccountNumber;

    @Column(name = "salary_account_last4", length = 4)
    private String salaryAccountLast4;

    @Column(name = "salary_ifsc", length = 11)
    private String salaryIfsc;
}

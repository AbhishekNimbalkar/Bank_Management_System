package com.bankmanagement.auth.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/** Login identity. The business profile (address, salary account...) lives in organization-service. */
@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
// Username is unique platform-wide among non-deleted rows (partial index ux_users_username, V3 migration).
@Table(name = "users")
@AttributeOverride(name = "id", column = @Column(name = "user_id"))
public class UserAccount extends TenantScopedEntity {

    @Column(nullable = false, length = 32)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(length = 120)
    private String email;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "employee_code", length = 20)
    private String employeeCode;

    @Column(name = "branch_id")
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    /** Locked by an administrator; only an administrator can unlock. */
    @Column(name = "admin_locked", nullable = false)
    private boolean adminLocked;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    /** How many automatic lockouts happened; drives the exponential back-off. */
    @Column(name = "lockout_count", nullable = false)
    private int lockoutCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public boolean isTemporarilyLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void clearFailures() {
        failedAttempts = 0;
        lockoutCount = 0;
        lockedUntil = null;
    }
}

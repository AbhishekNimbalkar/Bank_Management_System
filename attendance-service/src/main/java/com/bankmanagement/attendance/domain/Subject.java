package com.bankmanagement.attendance.domain;

import com.bankmanagement.common.security.AuthenticatedUser;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Snapshot of the employee a record belongs to, taken from the verified token at creation time. It
 * lets this service scope and approve records without calling organization-service on every read.
 */
@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class Subject {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "employee_name", nullable = false, length = 80)
    private String employeeName;

    @Column(name = "employee_code", length = 20)
    private String employeeCode;

    @Column(name = "employee_role", nullable = false, length = 10)
    private String employeeRole;

    @Column(name = "branch_id")
    private UUID branchId;

    public static Subject of(AuthenticatedUser user) {
        Subject s = new Subject();
        s.setUserId(user.userId());
        s.setEmployeeName(user.name());
        s.setEmployeeCode(user.employeeCode());
        s.setEmployeeRole(user.role());
        s.setBranchId(user.branchId());
        return s;
    }

    /** Embeddables must not be shared between entities; copy when reusing. */
    public Subject copy() {
        Subject s = new Subject();
        s.setUserId(userId);
        s.setEmployeeName(employeeName);
        s.setEmployeeCode(employeeCode);
        s.setEmployeeRole(employeeRole);
        s.setBranchId(branchId);
        return s;
    }
}

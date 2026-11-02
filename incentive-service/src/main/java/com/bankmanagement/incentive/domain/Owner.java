package com.bankmanagement.incentive.domain;

import com.bankmanagement.common.security.AuthenticatedUser;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Employee credited with a customer, snapshotted when the record is created. */
@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class Owner {

    @Column(name = "owner_user_id", nullable = false)
    private UUID userId;

    @Column(name = "owner_name", nullable = false, length = 80)
    private String name;

    @Column(name = "owner_code", length = 20)
    private String code;

    @Column(name = "owner_role", nullable = false, length = 10)
    private String role;

    @Column(name = "owner_designation", length = 60)
    private String designation;

    @Column(name = "owner_branch_id")
    private UUID branchId;

    public static Owner of(AuthenticatedUser user) {
        Owner o = new Owner();
        o.setUserId(user.userId());
        o.setName(user.name());
        o.setCode(user.employeeCode());
        o.setRole(user.role());
        o.setBranchId(user.branchId());
        return o;
    }
}

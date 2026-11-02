package com.bankmanagement.common.security;

import java.util.Arrays;

/**
 * Built-in platform roles ordered by seniority. Tenants may also define custom role codes;
 * those are treated with the lowest rank.
 */
public enum Role {
    SSA(5, "Super-Super Admin"),
    SA(4, "Super Admin"),
    HR(3, "HR"),
    BM(2, "Branch Manager"),
    EMP(1, "Employee");

    public static final String PLATFORM_TENANT = "platform";

    private final int rank;
    private final String label;

    Role(int rank, String label) {
        this.rank = rank;
        this.label = label;
    }

    public int rank() {
        return rank;
    }

    public String label() {
        return label;
    }

    public static boolean isBuiltIn(String code) {
        return Arrays.stream(values()).anyMatch(r -> r.name().equals(code));
    }

    /** Rank of any role code; unknown (custom) roles rank like an employee. */
    public static int rankOf(String code) {
        return Arrays.stream(values())
                .filter(r -> r.name().equals(code))
                .findFirst()
                .map(Role::rank)
                .orElse(EMP.rank);
    }
}

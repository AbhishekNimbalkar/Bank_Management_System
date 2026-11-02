package com.bankmanagement.attendance.repository;

import com.bankmanagement.common.security.DataScope;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

/** Tenant + data-scope filters for every entity that embeds a {@code Subject}. */
public final class ScopeSpecs {

    private ScopeSpecs() {
    }

    public static <T> Specification<T> inScope(String tenantCode, DataScope scope) {
        return (root, query, cb) -> {
            var tenant = cb.equal(root.get("tenantCode"), tenantCode);
            return switch (scope.level()) {
                case TENANT -> tenant;
                case BRANCH -> cb.and(tenant, cb.equal(root.get("subject").get("branchId"), scope.branchId()));
                case SELF -> cb.and(tenant, cb.equal(root.get("subject").get("userId"), scope.userId()));
            };
        };
    }

    public static <T> Specification<T> ofUser(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("subject").get("userId"), userId);
    }

    public static <T> Specification<T> between(String field, LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.between(root.get(field), from, to);
    }
}

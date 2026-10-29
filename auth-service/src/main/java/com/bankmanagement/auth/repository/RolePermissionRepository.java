package com.bankmanagement.auth.repository;

import com.bankmanagement.auth.domain.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByTenantCode(String tenantCode);

    List<RolePermission> findByTenantCodeAndRoleCode(String tenantCode, String roleCode);

    Optional<RolePermission> findByTenantCodeAndRoleCodeAndModule(String tenantCode, String roleCode, String module);

    @Modifying
    void deleteByTenantCode(String tenantCode);
}

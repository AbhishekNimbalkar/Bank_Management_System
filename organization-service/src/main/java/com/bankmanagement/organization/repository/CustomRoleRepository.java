package com.bankmanagement.organization.repository;

import com.bankmanagement.organization.domain.CustomRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomRoleRepository extends JpaRepository<CustomRole, UUID> {

    List<CustomRole> findByTenantCodeOrderByNameAsc(String tenantCode);

    Optional<CustomRole> findByIdAndTenantCode(UUID id, String tenantCode);

    boolean existsByTenantCodeAndCode(String tenantCode, String code);
}

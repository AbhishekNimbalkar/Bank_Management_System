package com.bankmanagement.organization.repository;

import com.bankmanagement.organization.domain.Designation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DesignationRepository extends JpaRepository<Designation, UUID> {

    List<Designation> findByTenantCodeOrderByTitleAsc(String tenantCode);

    Optional<Designation> findByIdAndTenantCode(UUID id, String tenantCode);

    boolean existsByTenantCodeAndTitleIgnoreCase(String tenantCode, String title);

    boolean existsByTenantCodeAndTitleIgnoreCaseAndIdNot(String tenantCode, String title, UUID id);
}

package com.bankmanagement.attendance.repository;

import com.bankmanagement.attendance.domain.LeavePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, UUID> {

    List<LeavePolicy> findByTenantCodeOrderByNameAsc(String tenantCode);

    Optional<LeavePolicy> findByIdAndTenantCode(UUID id, String tenantCode);

    boolean existsByTenantCode(String tenantCode);

    boolean existsByTenantCodeAndCode(String tenantCode, String code);
}

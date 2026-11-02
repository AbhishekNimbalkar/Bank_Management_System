package com.bankmanagement.organization.repository;

import com.bankmanagement.organization.domain.Employee;
import com.bankmanagement.organization.domain.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByIdAndTenantCode(UUID id, String tenantCode);

    Optional<Employee> findByTenantCodeAndUserId(String tenantCode, UUID userId);

    boolean existsByUsername(String username);

    long countByTenantCodeAndStatus(String tenantCode, RecordStatus status);

    long countByTenantCodeAndBranchId(String tenantCode, UUID branchId);

    long countByTenantCodeAndBranchIdAndStatus(String tenantCode, UUID branchId, RecordStatus status);

    long countByTenantCodeAndDesignation(String tenantCode, String designation);

    long countByTenantCodeAndRole(String tenantCode, String role);

    long countByStatus(RecordStatus status);

    /** Active staff per bank / per branch across all tenants (platform view). Rows: [id, count]. */
    @Query("select e.bankId, count(e) from Employee e where e.status = :status group by e.bankId")
    List<Object[]> countPerBank(@Param("status") RecordStatus status);

    @Query("select e.branchId, count(e) from Employee e where e.status = :status group by e.branchId")
    List<Object[]> countPerBranch(@Param("status") RecordStatus status);

    @Query("select coalesce(max(e.codeNumber), 100) from Employee e where e.tenantCode = :tenant")
    int maxCodeNumber(@Param("tenant") String tenantCode);
}

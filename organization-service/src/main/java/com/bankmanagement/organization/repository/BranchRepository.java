package com.bankmanagement.organization.repository;

import com.bankmanagement.organization.domain.Branch;
import com.bankmanagement.organization.domain.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    List<Branch> findByTenantCodeOrderByNameAsc(String tenantCode);

    List<Branch> findByTenantCodeAndBankIdOrderByNameAsc(String tenantCode, UUID bankId);

    Optional<Branch> findByIdAndTenantCode(UUID id, String tenantCode);

    boolean existsByTenantCodeAndIfsc(String tenantCode, String ifsc);

    boolean existsByTenantCodeAndIfscAndIdNot(String tenantCode, String ifsc, UUID id);

    long countByTenantCode(String tenantCode);

    long countByTenantCodeAndStatus(String tenantCode, RecordStatus status);

    /** Branch count per bank across all tenants (platform view). Rows: [bankId, count]. */
    @Query("select b.bankId, count(b) from Branch b group by b.bankId")
    List<Object[]> countPerBank();

    long countByTenantCodeAndBankId(String tenantCode, UUID bankId);
}

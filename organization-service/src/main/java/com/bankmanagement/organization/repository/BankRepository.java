package com.bankmanagement.organization.repository;

import com.bankmanagement.organization.domain.Bank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankRepository extends JpaRepository<Bank, UUID> {

    List<Bank> findByTenantCodeOrderByNameAsc(String tenantCode);

    Optional<Bank> findByIdAndTenantCode(UUID id, String tenantCode);

    boolean existsByTenantCodeAndCode(String tenantCode, String code);

    long countByTenantCode(String tenantCode);
}

package com.bankmanagement.organization.repository;

import com.bankmanagement.organization.domain.BankLogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BankLogoRepository extends JpaRepository<BankLogo, UUID> {

    Optional<BankLogo> findByBankIdAndTenantCode(UUID bankId, String tenantCode);

    boolean existsByBankIdAndTenantCode(UUID bankId, String tenantCode);

    @Query("select l.bankId from BankLogo l where l.tenantCode = :tenant")
    Set<UUID> bankIdsWithLogo(@Param("tenant") String tenant);
}

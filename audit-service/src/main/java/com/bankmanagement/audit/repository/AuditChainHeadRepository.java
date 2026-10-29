package com.bankmanagement.audit.repository;

import com.bankmanagement.audit.domain.AuditChainHead;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuditChainHeadRepository extends JpaRepository<AuditChainHead, String> {

    /** Race-free creation of a tenant's chain head. */
    @Modifying
    @Query(value = "INSERT INTO audit_chain_heads (tenant_code, last_sequence, last_hash) "
            + "VALUES (:tenant, 0, 'GENESIS') ON CONFLICT (tenant_code) DO NOTHING", nativeQuery = true)
    void createIfAbsent(@Param("tenant") String tenantCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuditChainHead h where h.tenantCode = :tenant")
    Optional<AuditChainHead> lock(@Param("tenant") String tenantCode);
}

package com.bankmanagement.audit.repository;

import com.bankmanagement.audit.domain.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    /** Chain walk in insertion order, used by verification. */
    Slice<AuditLog> findByTenantCodeOrderBySequenceAsc(String tenantCode, Pageable pageable);
}

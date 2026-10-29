package com.bankmanagement.organization.repository;

import com.bankmanagement.organization.domain.Tenant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByCode(String code);

    boolean existsByCode(String code);

    long countByStatus(Tenant.Status status);

    /** Serializes quota checks of one tenant so concurrent creates cannot exceed a limit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.code = :code")
    Optional<Tenant> lockByCode(@Param("code") String code);
}

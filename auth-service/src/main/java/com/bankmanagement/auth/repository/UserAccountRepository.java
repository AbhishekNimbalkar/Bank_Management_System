package com.bankmanagement.auth.repository;

import com.bankmanagement.auth.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByTenantCodeAndUsername(String tenantCode, String username);

    Optional<UserAccount> findByIdAndTenantCode(UUID id, String tenantCode);

    boolean existsByTenantCodeAndUsername(String tenantCode, String username);

    boolean existsByRole(String role);
}

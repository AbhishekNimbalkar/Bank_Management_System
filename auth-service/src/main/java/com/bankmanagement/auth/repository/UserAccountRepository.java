package com.bankmanagement.auth.repository;

import com.bankmanagement.auth.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByIdAndTenantCode(UUID id, String tenantCode);

    boolean existsByUsername(String username);

    boolean existsByRole(String role);
}

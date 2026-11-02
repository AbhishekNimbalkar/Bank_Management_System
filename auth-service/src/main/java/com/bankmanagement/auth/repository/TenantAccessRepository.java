package com.bankmanagement.auth.repository;

import com.bankmanagement.auth.domain.TenantAccess;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantAccessRepository extends JpaRepository<TenantAccess, String> {
}

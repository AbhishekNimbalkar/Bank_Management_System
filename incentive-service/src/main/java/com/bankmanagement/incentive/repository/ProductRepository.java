package com.bankmanagement.incentive.repository;

import com.bankmanagement.incentive.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByTenantCodeOrderByTypeAscNameAsc(String tenantCode);

    Optional<Product> findByIdAndTenantCode(UUID id, String tenantCode);

    boolean existsByTenantCode(String tenantCode);

    boolean existsByTenantCodeAndNameIgnoreCase(String tenantCode, String name);

    boolean existsByTenantCodeAndNameIgnoreCaseAndIdNot(String tenantCode, String name, UUID id);
}

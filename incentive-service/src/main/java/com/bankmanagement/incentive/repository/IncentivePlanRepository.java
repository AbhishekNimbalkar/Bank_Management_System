package com.bankmanagement.incentive.repository;

import com.bankmanagement.incentive.domain.Enums;
import com.bankmanagement.incentive.domain.IncentivePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncentivePlanRepository extends JpaRepository<IncentivePlan, UUID> {

    List<IncentivePlan> findByTenantCodeAndMonthOrderByNameAsc(String tenantCode, String month);

    List<IncentivePlan> findByTenantCodeOrderByMonthDescNameAsc(String tenantCode);

    List<IncentivePlan> findByTenantCodeAndMonthAndStatusOrderByNameAsc(String tenantCode, String month,
                                                                        Enums.PlanStatus status);

    Optional<IncentivePlan> findByIdAndTenantCode(UUID id, String tenantCode);

    boolean existsByTenantCodeAndMonthAndNameIgnoreCase(String tenantCode, String month, String name);
}

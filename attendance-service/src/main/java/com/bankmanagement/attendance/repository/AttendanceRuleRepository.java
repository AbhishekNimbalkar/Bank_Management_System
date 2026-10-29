package com.bankmanagement.attendance.repository;

import com.bankmanagement.attendance.domain.AttendanceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AttendanceRuleRepository extends JpaRepository<AttendanceRule, UUID> {

    Optional<AttendanceRule> findByTenantCode(String tenantCode);
}

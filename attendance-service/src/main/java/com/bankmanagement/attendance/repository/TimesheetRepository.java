package com.bankmanagement.attendance.repository;

import com.bankmanagement.attendance.domain.Enums;
import com.bankmanagement.attendance.domain.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TimesheetRepository extends JpaRepository<Timesheet, UUID>, JpaSpecificationExecutor<Timesheet> {

    Optional<Timesheet> findByIdAndTenantCode(UUID id, String tenantCode);

    @Query("""
            select coalesce(sum(t.hours), 0) from Timesheet t
            where t.tenantCode = :tenant and t.subject.userId = :userId and t.workDate = :date
              and t.status <> :excluded
            """)
    BigDecimal hoursOn(@Param("tenant") String tenant, @Param("userId") UUID userId, @Param("date") LocalDate date,
                       @Param("excluded") Enums.TimesheetStatus excluded);
}

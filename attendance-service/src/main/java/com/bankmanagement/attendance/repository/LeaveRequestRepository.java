package com.bankmanagement.attendance.repository;

import com.bankmanagement.attendance.domain.Enums;
import com.bankmanagement.attendance.domain.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID>, JpaSpecificationExecutor<LeaveRequest> {

    Optional<LeaveRequest> findByIdAndTenantCode(UUID id, String tenantCode);

    /** Days already used or reserved for one policy in a calendar year. */
    @Query("""
            select coalesce(sum(l.days), 0) from LeaveRequest l
            where l.tenantCode = :tenant and l.subject.userId = :userId and l.policyId = :policyId
              and l.status in :statuses and l.fromDate between :yearStart and :yearEnd
            """)
    BigDecimal sumDays(@Param("tenant") String tenant, @Param("userId") UUID userId, @Param("policyId") UUID policyId,
                       @Param("statuses") Collection<Enums.RequestStatus> statuses,
                       @Param("yearStart") LocalDate yearStart, @Param("yearEnd") LocalDate yearEnd);

    @Query("""
            select count(l) from LeaveRequest l
            where l.tenantCode = :tenant and l.subject.userId = :userId and l.status in :statuses
              and l.fromDate <= :to and l.toDate >= :from
            """)
    long countOverlapping(@Param("tenant") String tenant, @Param("userId") UUID userId,
                          @Param("statuses") Collection<Enums.RequestStatus> statuses,
                          @Param("from") LocalDate from, @Param("to") LocalDate to);
}

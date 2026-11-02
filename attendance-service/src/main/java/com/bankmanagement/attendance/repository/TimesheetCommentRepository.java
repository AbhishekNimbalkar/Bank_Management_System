package com.bankmanagement.attendance.repository;

import com.bankmanagement.attendance.domain.TimesheetComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimesheetCommentRepository extends JpaRepository<TimesheetComment, UUID> {

    List<TimesheetComment> findByTenantCodeAndTimesheetIdOrderByCreatedAtAsc(String tenantCode, UUID timesheetId);

    Optional<TimesheetComment> findByIdAndTenantCodeAndTimesheetId(UUID id, String tenantCode, UUID timesheetId);

    long countByTenantCodeAndTimesheetId(String tenantCode, UUID timesheetId);

    /** Rows: [timesheetId, count]. */
    @Query("""
            select c.timesheetId, count(c) from TimesheetComment c
            where c.tenantCode = :tenant and c.timesheetId in :ids group by c.timesheetId
            """)
    List<Object[]> countByTimesheet(@Param("tenant") String tenant, @Param("ids") Collection<UUID> ids);
}

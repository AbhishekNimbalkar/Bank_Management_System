package com.bankmanagement.attendance.service;

import com.bankmanagement.attendance.domain.AttendanceRecord;
import com.bankmanagement.attendance.domain.Enums.AttendanceStatus;
import com.bankmanagement.attendance.domain.Enums.RequestStatus;
import com.bankmanagement.attendance.domain.Enums.TimesheetStatus;
import com.bankmanagement.attendance.dto.AttendanceDto;
import com.bankmanagement.attendance.repository.AttendanceRecordRepository;
import com.bankmanagement.attendance.repository.LeaveRequestRepository;
import com.bankmanagement.attendance.repository.RegularizationRepository;
import com.bankmanagement.attendance.repository.ScopeSpecs;
import com.bankmanagement.attendance.repository.TimesheetRepository;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.DataScope;
import com.bankmanagement.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Attendance part of the dashboard: today's headcount in scope, my punch and my approval queue. */
@Service
@RequiredArgsConstructor
public class AttendanceDashboardService {

    private final AttendanceRecordRepository records;
    private final LeaveRequestRepository leaves;
    private final RegularizationRepository regularizations;
    private final TimesheetRepository timesheets;
    private final Clock clock;

    /**
     * {@code byStatus} counts records marked today; anyone without a record has not clocked in yet
     * (the organization dashboard supplies the active-staff total to derive "not marked").
     * Pending counts exclude the caller's own requests, so they reflect what the caller can act on.
     */
    public record Summary(LocalDate date, Map<AttendanceStatus, Long> byStatus, long markedToday,
                          AttendanceDto.Response myToday, long pendingLeaves, long pendingCorrections,
                          long pendingTimesheets) {
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        DataScope scope = DataScope.of(user);
        LocalDate today = LocalDate.now(clock);

        List<AttendanceRecord> todays = records.findAll(ScopeSpecs.<AttendanceRecord>inScope(tenant, scope)
                .and(ScopeSpecs.between("workDate", today, today)));
        Map<AttendanceStatus, Long> byStatus = new EnumMap<>(AttendanceStatus.class);
        for (AttendanceStatus s : AttendanceStatus.values()) {
            byStatus.put(s, 0L);
        }
        todays.forEach(r -> byStatus.merge(r.getStatus(), 1L, Long::sum));
        AttendanceDto.Response mine = todays.stream()
                .filter(r -> r.getSubject().getUserId().equals(user.userId()))
                .findFirst().map(AttendanceDto.Response::from).orElse(null);

        boolean approver = scope.level() != DataScope.Level.SELF;
        long pendingLeaves = approver ? leaves.count(othersPending(tenant, scope, user, RequestStatus.PENDING)) : 0;
        long pendingRegs = approver ? regularizations.count(othersPending(tenant, scope, user, RequestStatus.PENDING)) : 0;
        long pendingSheets = approver ? timesheets.count(othersPending(tenant, scope, user, TimesheetStatus.SUBMITTED)) : 0;

        return new Summary(today, byStatus, todays.size(), mine, pendingLeaves, pendingRegs, pendingSheets);
    }

    private static <T> Specification<T> othersPending(String tenant, DataScope scope, AuthenticatedUser user,
                                                      Enum<?> status) {
        Specification<T> notMine = (root, q, cb) -> cb.notEqual(root.get("subject").get("userId"), user.userId());
        Specification<T> pending = (root, q, cb) -> cb.equal(root.get("status"), status);
        return ScopeSpecs.<T>inScope(tenant, scope).and(notMine).and(pending);
    }
}

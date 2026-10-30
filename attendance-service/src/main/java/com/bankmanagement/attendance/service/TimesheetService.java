package com.bankmanagement.attendance.service;

import com.bankmanagement.attendance.domain.Enums.TimesheetStatus;
import com.bankmanagement.attendance.domain.Subject;
import com.bankmanagement.attendance.domain.Timesheet;
import com.bankmanagement.attendance.dto.DecisionRequest;
import com.bankmanagement.attendance.dto.TimesheetDto;
import com.bankmanagement.attendance.repository.ScopeSpecs;
import com.bankmanagement.attendance.repository.TimesheetRepository;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.DataScope;
import com.bankmanagement.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TimesheetService {

    private static final int MAX_DAYS_BACK = 14;
    private static final BigDecimal MAX_HOURS_PER_DAY = BigDecimal.valueOf(24);

    private final TimesheetRepository timesheets;
    private final AuditPublisher audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TimesheetDto.Response> list() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return timesheets.findAll(ScopeSpecs.inScope(SecurityUtils.currentTenant(), DataScope.of(user)),
                        Sort.by(Sort.Direction.DESC, "workDate"))
                .stream().map(TimesheetDto.Response::from).toList();
    }

    @Transactional
    public TimesheetDto.Response create(TimesheetDto.Request req) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        LocalDate today = LocalDate.now(clock);
        if (req.workDate().isAfter(today)) {
            throw BusinessException.validation("Timesheets cannot be filed for a future date.", "workDate");
        }
        if (req.workDate().isBefore(today.minusDays(MAX_DAYS_BACK))) {
            throw BusinessException.validation("Timesheets can only be filed for the last " + MAX_DAYS_BACK + " days.",
                    "workDate");
        }
        BigDecimal logged = timesheets.hoursOn(tenant, user.userId(), req.workDate(), TimesheetStatus.REJECTED);
        if (logged.add(req.hours()).compareTo(MAX_HOURS_PER_DAY) > 0) {
            throw BusinessException.validation("That would exceed 24 hours for " + req.workDate() + " (already "
                    + logged + " h logged).", "hours");
        }
        Timesheet t = new Timesheet();
        t.setTenantCode(tenant);
        t.setSubject(Subject.of(user));
        t.setWorkDate(req.workDate());
        t.setTask(req.task().trim());
        t.setHours(req.hours());
        t.setNotes(req.notes());
        t.setStatus(TimesheetStatus.SUBMITTED);
        timesheets.save(t);
        audit.record("timesheets", "Logged timesheet", req.hours() + " h on " + req.workDate());
        return TimesheetDto.Response.from(t);
    }

    /** Own entry, only while it awaits approval; the 24-hour daily cap is re-checked. */
    @Transactional
    public TimesheetDto.Response update(UUID id, TimesheetDto.Request req) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        Timesheet t = ownPending(id);
        LocalDate today = LocalDate.now(clock);
        if (req.workDate().isAfter(today) || req.workDate().isBefore(today.minusDays(MAX_DAYS_BACK))) {
            throw BusinessException.validation("Timesheets can only cover the last " + MAX_DAYS_BACK + " days.", "workDate");
        }
        BigDecimal logged = timesheets.hoursOn(tenant, user.userId(), req.workDate(), TimesheetStatus.REJECTED);
        if (req.workDate().equals(t.getWorkDate())) {
            logged = logged.subtract(t.getHours());
        }
        if (logged.add(req.hours()).compareTo(MAX_HOURS_PER_DAY) > 0) {
            throw BusinessException.validation("That would exceed 24 hours for " + req.workDate() + ".", "hours");
        }
        t.setWorkDate(req.workDate());
        t.setTask(req.task().trim());
        t.setHours(req.hours());
        t.setNotes(req.notes());
        audit.record("timesheets", "Edited timesheet", req.hours() + " h on " + req.workDate());
        return TimesheetDto.Response.from(t);
    }

    @Transactional
    public void delete(UUID id) {
        Timesheet t = ownPending(id);
        t.markDeleted();
        audit.record("timesheets", "Deleted timesheet", t.getHours() + " h on " + t.getWorkDate());
    }

    private Timesheet ownPending(UUID id) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        Timesheet t = timesheets.findByIdAndTenantCode(id, SecurityUtils.currentTenant())
                .filter(x -> x.getSubject().getUserId().equals(user.userId()))
                .orElseThrow(() -> BusinessException.notFound("Timesheet entry"));
        if (t.getStatus() != TimesheetStatus.SUBMITTED) {
            throw BusinessException.rule("Approved or rejected entries cannot be changed.");
        }
        return t;
    }

    @Transactional
    public TimesheetDto.Response decide(UUID id, DecisionRequest decision) {
        AuthenticatedUser approver = SecurityUtils.currentUser();
        Timesheet t = timesheets.findByIdAndTenantCode(id, SecurityUtils.currentTenant())
                .orElseThrow(() -> BusinessException.notFound("Timesheet entry"));
        if (t.getStatus() != TimesheetStatus.SUBMITTED) {
            throw BusinessException.rule("This entry was already " + t.getStatus().name().toLowerCase() + ".");
        }
        Subject s = t.getSubject();
        DataScope.assertCanDecide(approver, s.getUserId(), s.getBranchId(), s.getEmployeeRole());
        t.setStatus(decision.approved() ? TimesheetStatus.APPROVED : TimesheetStatus.REJECTED);
        t.setDecidedBy(approver.userId());
        t.setDecidedAt(Instant.now(clock));
        audit.record("timesheets", decision.approved() ? "Approved timesheet" : "Rejected timesheet",
                s.getEmployeeName() + " - " + t.getWorkDate());
        return TimesheetDto.Response.from(t);
    }
}

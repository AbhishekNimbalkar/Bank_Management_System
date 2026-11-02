package com.bankmanagement.attendance.service;

import com.bankmanagement.attendance.domain.Enums.TimesheetStatus;
import com.bankmanagement.attendance.domain.Subject;
import com.bankmanagement.attendance.domain.Timesheet;
import com.bankmanagement.attendance.domain.TimesheetComment;
import com.bankmanagement.attendance.dto.DecisionRequest;
import com.bankmanagement.attendance.dto.TimesheetDto;
import com.bankmanagement.attendance.repository.ScopeSpecs;
import com.bankmanagement.attendance.repository.TimesheetCommentRepository;
import com.bankmanagement.attendance.repository.TimesheetRepository;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.DataScope;
import com.bankmanagement.common.security.Role;
import com.bankmanagement.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TimesheetService {

    private static final int MAX_DAYS_BACK = 14;
    private static final BigDecimal MAX_HOURS_PER_DAY = BigDecimal.valueOf(24);

    private final TimesheetRepository timesheets;
    private final TimesheetCommentRepository comments;
    private final AuditPublisher audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TimesheetDto.Response> list() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        List<Timesheet> rows = timesheets.findAll(ScopeSpecs.inScope(tenant, DataScope.of(user)),
                Sort.by(Sort.Direction.DESC, "workDate"));
        Map<UUID, Long> counts = new HashMap<>();
        if (!rows.isEmpty()) {
            comments.countByTimesheet(tenant, rows.stream().map(Timesheet::getId).toList())
                    .forEach(r -> counts.put((UUID) r[0], (Long) r[1]));
        }
        return rows.stream().map(t -> TimesheetDto.Response.from(t, counts.getOrDefault(t.getId(), 0L))).toList();
    }

    /** Comments on an entry the caller can see: the employee, their managers in scope and the Super Admin. */
    @Transactional(readOnly = true)
    public List<TimesheetDto.CommentResponse> comments(UUID timesheetId) {
        String tenant = SecurityUtils.currentTenant();
        visible(timesheetId);
        return comments.findByTenantCodeAndTimesheetIdOrderByCreatedAtAsc(tenant, timesheetId).stream()
                .map(TimesheetDto.CommentResponse::from).toList();
    }

    /** Only a manager above the employee (in scope) can comment; the employee reads the comments. */
    @Transactional
    public TimesheetDto.CommentResponse addComment(UUID timesheetId, TimesheetDto.CommentRequest req) {
        AuthenticatedUser author = SecurityUtils.currentUser();
        Timesheet t = visible(timesheetId);
        Subject s = t.getSubject();
        if (author.userId().equals(s.getUserId())) {
            throw BusinessException.forbidden("You cannot comment on your own timesheet.");
        }
        boolean topAdmin = author.isPlatformOwner() || Role.SA.name().equals(author.role());
        if (!topAdmin && Role.rankOf(s.getEmployeeRole()) >= author.rank()) {
            throw BusinessException.forbidden("Only a manager senior to the employee can comment.");
        }
        TimesheetComment c = new TimesheetComment();
        c.setTenantCode(SecurityUtils.currentTenant());
        c.setTimesheetId(t.getId());
        c.setAuthorUserId(author.userId());
        c.setAuthorName(author.name());
        c.setAuthorRole(author.role());
        c.setBody(req.comment().trim());
        comments.save(c);
        audit.record("timesheets", "Commented on timesheet", s.getEmployeeName() + " - " + t.getWorkDate());
        return TimesheetDto.CommentResponse.from(c);
    }

    /** Authors remove their own comments. */
    @Transactional
    public void deleteComment(UUID timesheetId, UUID commentId) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        visible(timesheetId);
        TimesheetComment c = comments.findByIdAndTenantCodeAndTimesheetId(commentId, SecurityUtils.currentTenant(),
                        timesheetId)
                .orElseThrow(() -> BusinessException.notFound("Comment"));
        if (!c.getAuthorUserId().equals(user.userId())) {
            throw BusinessException.forbidden("You can only delete your own comments.");
        }
        c.markDeleted();
        audit.record("timesheets", "Deleted timesheet comment", "On entry of " + timesheetId);
    }

    private Timesheet visible(UUID timesheetId) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        Timesheet t = timesheets.findByIdAndTenantCode(timesheetId, SecurityUtils.currentTenant())
                .orElseThrow(() -> BusinessException.notFound("Timesheet entry"));
        if (!DataScope.of(user).includes(t.getSubject().getBranchId(), t.getSubject().getUserId())) {
            throw BusinessException.notFound("Timesheet entry");
        }
        return t;
    }

    private TimesheetDto.Response response(Timesheet t) {
        return TimesheetDto.Response.from(t, t.getId() == null ? 0
                : comments.countByTenantCodeAndTimesheetId(t.getTenantCode(), t.getId()));
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
        return response(t);
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
        return response(t);
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
        return response(t);
    }
}

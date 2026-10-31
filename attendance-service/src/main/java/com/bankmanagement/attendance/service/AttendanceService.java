package com.bankmanagement.attendance.service;

import com.bankmanagement.attendance.client.OrganizationClient;
import com.bankmanagement.attendance.domain.AttendanceRecord;
import com.bankmanagement.attendance.domain.AttendanceRule;
import com.bankmanagement.attendance.domain.Enums.AttendanceSource;
import com.bankmanagement.attendance.domain.Enums.AttendanceStatus;
import com.bankmanagement.attendance.domain.Subject;
import com.bankmanagement.attendance.dto.AttendanceDto;
import com.bankmanagement.attendance.repository.AttendanceRecordRepository;
import com.bankmanagement.attendance.repository.ScopeSpecs;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.DataScope;
import com.bankmanagement.common.security.Role;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.common.web.CsvWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final Set<AttendanceStatus> LEAVE_STATUSES =
            Set.of(AttendanceStatus.ON_LEAVE, AttendanceStatus.LWP);
    /** Statuses that may be saved without clock times. */
    private static final Set<AttendanceStatus> NO_TIME_STATUSES =
            Set.of(AttendanceStatus.ABSENT, AttendanceStatus.ON_LEAVE, AttendanceStatus.LWP, AttendanceStatus.HALF_DAY_LEAVE);

    private final AttendanceRecordRepository records;
    private final AttendanceRuleService ruleService;
    private final OrganizationClient organizationClient;
    private final AuditPublisher audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AttendanceDto.Response> list(YearMonth month, UUID userId) {
        return query(month, userId).stream().map(AttendanceDto.Response::from).toList();
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(YearMonth month, UUID userId) {
        List<AttendanceRecord> rows = query(month, userId);
        List<Function<AttendanceRecord, Object>> columns = List.of(
                AttendanceRecord::getWorkDate, r -> r.getSubject().getEmployeeName(), r -> r.getSubject().getEmployeeCode(),
                AttendanceRecord::getCheckIn, AttendanceRecord::getCheckOut, AttendanceRecord::getHoursWorked,
                AttendanceRecord::getStatus, AttendanceRecord::getDistanceMeters, AttendanceRecord::getNote);
        audit.record("attendance", "Exported attendance", month + " (" + rows.size() + " rows)");
        return CsvWriter.write(List.of("Date", "Employee", "Code", "In", "Out", "Hours", "Status", "Distance (m)", "Note"),
                rows, columns);
    }

    @Transactional(readOnly = true)
    public AttendanceDto.Response today() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return records.findByTenantCodeAndSubjectUserIdAndWorkDate(SecurityUtils.currentTenant(), user.userId(),
                LocalDate.now(clock)).map(AttendanceDto.Response::from).orElse(null);
    }

    /**
     * Clock-in is accepted only inside the branch geofence. The location is checked server-side
     * against the branch coordinates; the client cannot choose its own branch or radius.
     */
    @Transactional
    public AttendanceDto.Response checkIn(AttendanceDto.CheckInRequest req) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        if (user.impersonatorId() != null) {
            throw BusinessException.forbidden("Attendance cannot be marked during a support session.");
        }
        if (user.branchId() == null) {
            throw BusinessException.rule("You are not assigned to a branch. Contact HR.");
        }
        AttendanceRule rule = ruleService.rulesFor(tenant);
        // The organization's plan decides whether clock-in must happen inside the branch geofence.
        OrganizationClient.Entitlements entitlements = organizationClient.entitlements().data();
        boolean geofenced = entitlements.features() == null || entitlements.features().geofencing();
        if (geofenced && req.accuracyMeters() != null && req.accuracyMeters() > rule.getMaxGpsAccuracyMeters()) {
            throw BusinessException.rule("Location is too imprecise (" + Math.round(req.accuracyMeters())
                    + " m). Move to an open area and try again.");
        }

        OrganizationClient.Geofence branch = organizationClient.geofence(user.branchId()).data();
        if (!branch.active()) {
            throw BusinessException.rule("Your branch is not active.");
        }
        int distance = GeoUtils.distanceMeters(req.latitude(), req.longitude(), branch.latitude(), branch.longitude());
        if (geofenced && distance > branch.radiusMeters()) {
            throw BusinessException.rule("You are " + distance + " m away from " + branch.name()
                    + ". Clock-in is allowed only within " + branch.radiusMeters() + " m of your branch.");
        }

        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        AttendanceRecord record = records.findByTenantCodeAndSubjectUserIdAndWorkDate(tenant, user.userId(), today)
                .orElse(null);
        if (record != null && record.getCheckIn() != null) {
            throw BusinessException.conflict("You already clocked in today at " + record.getCheckIn() + ".", "checkIn");
        }
        if (record != null && LEAVE_STATUSES.contains(record.getStatus())) {
            throw BusinessException.rule("You are on approved full-day leave today.");
        }
        if (record == null) {
            record = new AttendanceRecord();
            record.setTenantCode(tenant);
            record.setSubject(Subject.of(user));
            record.setWorkDate(today);
        }

        LocalTime lateAfter = rule.getShiftStart().plusMinutes(rule.getGraceMinutes());
        boolean late = now.isAfter(lateAfter);
        record.setCheckIn(now);
        record.setDistanceMeters(distance);
        record.setSource(AttendanceSource.GPS);
        record.setLatitude(req.latitude());
        record.setLongitude(req.longitude());
        if (record.getStatus() != AttendanceStatus.HALF_DAY_LEAVE) {
            record.setStatus(late ? AttendanceStatus.LATE : AttendanceStatus.PRESENT);
        }
        record.setNote(late ? "Clocked in after " + lateAfter : null);
        records.save(record);

        audit.record("attendance", "Geofence clock-in", "At " + now + ", " + distance + " m from " + branch.name());
        return AttendanceDto.Response.from(record);
    }

    @Transactional
    public AttendanceDto.Response checkOut() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        if (user.impersonatorId() != null) {
            throw BusinessException.forbidden("Attendance cannot be marked during a support session.");
        }
        LocalDate today = LocalDate.now(clock);
        AttendanceRecord record = records.findByTenantCodeAndSubjectUserIdAndWorkDate(tenant, user.userId(), today)
                .filter(r -> r.getCheckIn() != null)
                .orElseThrow(() -> BusinessException.rule("No clock-in found for today. Please clock in first."));
        if (record.getCheckOut() != null) {
            throw BusinessException.conflict("You already clocked out today at " + record.getCheckOut() + ".", "checkOut");
        }
        LocalTime now = LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        record.setCheckOut(now);
        record.setHoursWorked(hoursBetween(record.getCheckIn(), now));

        AttendanceRule rule = ruleService.rulesFor(tenant);
        if (record.getStatus() == AttendanceStatus.PRESENT && record.getHoursWorked().compareTo(rule.getHalfDayHours()) < 0) {
            record.setStatus(AttendanceStatus.HALF_DAY);
        }
        audit.record("attendance", "Clock-out", "At " + now + " (" + record.getHoursWorked() + " h)");
        return AttendanceDto.Response.from(record);
    }

    /**
     * A manager marks a day for an employee who could not clock in. The employee is loaded from
     * organization-service with the caller's token, so a Branch Manager can only reach their own staff.
     */
    @Transactional
    public AttendanceDto.Response markManual(AttendanceDto.ManualRequest req) {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        if (actor.impersonatorId() != null) {
            throw BusinessException.forbidden("Attendance cannot be marked during a support session.");
        }
        if (req.workDate().isAfter(LocalDate.now(clock))) {
            throw BusinessException.validation("Attendance cannot be marked for a future date.", "workDate");
        }
        if (req.checkOut() != null && !req.checkOut().isAfter(req.checkIn())) {
            throw BusinessException.validation("Check-out must be after check-in.", "checkOut");
        }
        OrganizationClient.Employee employee = organizationClient.employee(req.employeeId()).data();
        if (employee.userId() == null || !"ACTIVE".equals(employee.status())) {
            throw BusinessException.rule("Attendance can only be marked for an active employee with a login.");
        }
        assertCanManage(actor, employee.userId(), employee.branchId(), employee.role(), "mark attendance for");

        AttendanceRecord record = records.findByTenantCodeAndSubjectUserIdAndWorkDate(tenant, employee.userId(),
                req.workDate()).orElse(null);
        if (record != null && record.getCheckIn() != null) {
            throw BusinessException.conflict(employee.name() + " already has attendance on " + req.workDate()
                    + ". HR or the Super Admin can edit it.", "workDate");
        }
        if (record != null && LEAVE_STATUSES.contains(record.getStatus())) {
            throw BusinessException.rule(employee.name() + " is on approved full-day leave on " + req.workDate() + ".");
        }
        if (record == null) {
            record = new AttendanceRecord();
            record.setTenantCode(tenant);
            record.setSubject(Subject.of(employee.userId(), employee.name(), employee.code(), employee.role(),
                    employee.branchId()));
            record.setWorkDate(req.workDate());
        }

        Integer distance = null;
        if (employee.branchId() != null) {
            OrganizationClient.Geofence branch = organizationClient.geofence(employee.branchId()).data();
            distance = GeoUtils.distanceMeters(req.latitude(), req.longitude(), branch.latitude(), branch.longitude());
        }
        record.setCheckIn(req.checkIn());
        record.setCheckOut(req.checkOut());
        record.setHoursWorked(req.checkOut() == null ? null : hoursBetween(req.checkIn(), req.checkOut()));
        record.setDistanceMeters(distance);
        record.setLatitude(req.latitude());
        record.setLongitude(req.longitude());
        record.setSource(AttendanceSource.MANUAL);
        record.setMarkedBy(actor.userId());
        record.setMarkedByName(actor.name());
        record.setNote(req.reason().trim());
        record.setStatus(req.status() != null ? req.status()
                : derivedStatus(ruleService.rulesFor(tenant), req.checkIn(), record.getHoursWorked(), record.getStatus()));
        records.save(record);

        audit.record("attendance", "Manual attendance", employee.name() + " on " + req.workDate() + " "
                + req.checkIn() + "-" + (req.checkOut() == null ? "open" : req.checkOut()) + ", "
                + (distance == null ? "no branch" : distance + " m from branch") + ": " + req.reason().trim());
        return AttendanceDto.Response.from(record);
    }

    /** HR and the Super Admin correct a record; the previous values go to the audit log. */
    @Transactional
    public AttendanceDto.Response edit(UUID id, AttendanceDto.EditRequest req) {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        if (!actor.isPlatformOwner() && !Role.SA.name().equals(actor.role()) && !Role.HR.name().equals(actor.role())) {
            throw BusinessException.forbidden("Only HR or the Super Admin can edit attendance.");
        }
        AttendanceRecord record = records.findByIdAndTenantCode(id, SecurityUtils.currentTenant())
                .orElseThrow(() -> BusinessException.notFound("Attendance record"));
        Subject s = record.getSubject();
        assertCanManage(actor, s.getUserId(), s.getBranchId(), s.getEmployeeRole(), "edit the attendance of");

        boolean needsTimes = !NO_TIME_STATUSES.contains(req.status());
        if (needsTimes && req.checkIn() == null) {
            throw BusinessException.validation("Check-in time is required for status " + req.status() + ".", "checkIn");
        }
        if (req.checkIn() == null && req.checkOut() != null) {
            throw BusinessException.validation("Check-out needs a check-in time.", "checkOut");
        }
        if (req.checkIn() != null && req.checkOut() != null && !req.checkOut().isAfter(req.checkIn())) {
            throw BusinessException.validation("Check-out must be after check-in.", "checkOut");
        }

        String before = record.getStatus() + " " + record.getCheckIn() + "-" + record.getCheckOut();
        record.setCheckIn(req.checkIn());
        record.setCheckOut(req.checkOut());
        record.setHoursWorked(req.checkIn() != null && req.checkOut() != null
                ? hoursBetween(req.checkIn(), req.checkOut()) : null);
        record.setStatus(req.status());
        record.setEditedBy(actor.userId());
        record.setEditedByName(actor.name());
        record.setEditedAt(Instant.now(clock));
        record.setEditReason(req.reason().trim());

        audit.record("attendance", "Edited attendance", s.getEmployeeName() + " on " + record.getWorkDate() + ": "
                + before + " -> " + req.status() + " " + req.checkIn() + "-" + req.checkOut() + " (" + req.reason().trim() + ")");
        return AttendanceDto.Response.from(record);
    }

    /** Never your own record; the employee must be in scope and junior to you (the Super Admin manages all). */
    private static void assertCanManage(AuthenticatedUser actor, UUID userId, UUID branchId, String role, String what) {
        if (actor.userId().equals(userId)) {
            throw BusinessException.forbidden("You cannot " + what + " yourself.");
        }
        if (!DataScope.of(actor).includes(branchId, userId)) {
            throw BusinessException.forbidden("This employee is outside your scope.");
        }
        boolean topAdmin = actor.isPlatformOwner() || Role.SA.name().equals(actor.role());
        if (!topAdmin && Role.rankOf(role) >= actor.rank()) {
            throw BusinessException.forbidden("You can only " + what + " employees junior to you.");
        }
    }

    /** Same rules as a GPS clock-in: late after shift start + grace, half day below the threshold. */
    private static AttendanceStatus derivedStatus(AttendanceRule rule, LocalTime checkIn, BigDecimal hours,
                                                  AttendanceStatus current) {
        if (current == AttendanceStatus.HALF_DAY_LEAVE) {
            return current;
        }
        if (hours != null && hours.compareTo(rule.getHalfDayHours()) < 0) {
            return AttendanceStatus.HALF_DAY;
        }
        return checkIn.isAfter(rule.getShiftStart().plusMinutes(rule.getGraceMinutes()))
                ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
    }

    static BigDecimal hoursBetween(LocalTime in, LocalTime out) {
        long minutes = Math.max(0, Duration.between(in, out).toMinutes());
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private List<AttendanceRecord> query(YearMonth month, UUID userId) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        Specification<AttendanceRecord> spec = ScopeSpecs.<AttendanceRecord>inScope(SecurityUtils.currentTenant(),
                        DataScope.of(user))
                .and(ScopeSpecs.between("workDate", month.atDay(1), month.atEndOfMonth()));
        if (userId != null) {
            spec = spec.and(ScopeSpecs.ofUser(userId));
        }
        return records.findAll(spec, Sort.by(Sort.Direction.DESC, "workDate"));
    }
}

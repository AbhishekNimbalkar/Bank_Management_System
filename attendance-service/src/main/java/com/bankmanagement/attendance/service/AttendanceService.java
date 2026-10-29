package com.bankmanagement.attendance.service;

import com.bankmanagement.attendance.client.OrganizationClient;
import com.bankmanagement.attendance.domain.AttendanceRecord;
import com.bankmanagement.attendance.domain.AttendanceRule;
import com.bankmanagement.attendance.domain.Enums.AttendanceStatus;
import com.bankmanagement.attendance.domain.Subject;
import com.bankmanagement.attendance.dto.AttendanceDto;
import com.bankmanagement.attendance.repository.AttendanceRecordRepository;
import com.bankmanagement.attendance.repository.ScopeSpecs;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.DataScope;
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

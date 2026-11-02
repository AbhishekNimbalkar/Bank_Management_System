package com.bankmanagement.attendance.service;

import com.bankmanagement.attendance.domain.AttendanceRecord;
import com.bankmanagement.attendance.domain.Enums.AttendanceStatus;
import com.bankmanagement.attendance.domain.Enums.DayType;
import com.bankmanagement.attendance.domain.Enums.RequestStatus;
import com.bankmanagement.attendance.domain.LeavePolicy;
import com.bankmanagement.attendance.domain.LeaveRequest;
import com.bankmanagement.attendance.domain.Subject;
import com.bankmanagement.attendance.dto.DecisionRequest;
import com.bankmanagement.attendance.dto.LeaveDto;
import com.bankmanagement.attendance.repository.AttendanceRecordRepository;
import com.bankmanagement.attendance.repository.LeaveRequestRepository;
import com.bankmanagement.attendance.repository.ScopeSpecs;
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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private static final Set<RequestStatus> CHARGED = Set.of(RequestStatus.APPROVED, RequestStatus.PENDING);
    private static final int MAX_BACKDATE_DAYS = 7;
    private static final int MAX_SPAN_DAYS = 120;

    private final LeaveRequestRepository leaves;
    private final AttendanceRecordRepository records;
    private final LeavePolicyService policyService;
    private final HolidayService holidayService;
    private final AuditPublisher audit;
    private final Clock clock;

    @Transactional
    public List<LeaveDto.Response> list() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        Map<UUID, LeavePolicy> policies = policyMap(tenant);
        return leaves.findAll(ScopeSpecs.inScope(tenant, DataScope.of(user)), Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().map(l -> LeaveDto.Response.from(l, policies.get(l.getPolicyId()))).toList();
    }

    @Transactional
    public List<LeaveDto.Balance> myBalances() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        int year = LocalDate.now(clock).getYear();
        return policyService.policiesFor(tenant).stream().filter(LeavePolicy::isActive).map(p -> {
            BigDecimal used = used(tenant, user.userId(), p.getId(), year);
            BigDecimal remaining = p.getAnnualDays().subtract(used).max(BigDecimal.ZERO);
            return new LeaveDto.Balance(p.getId(), p.getName(), p.getCode(), p.getAnnualDays(), used, remaining,
                    p.isPaid(), p.isHalfDayAllowed());
        }).toList();
    }

    @Transactional
    public LeaveDto.Response apply(LeaveDto.ApplyRequest req) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        LocalDate today = LocalDate.now(clock);

        if (req.toDate().isBefore(req.fromDate())) {
            throw BusinessException.validation("The end date cannot be before the start date.", "toDate");
        }
        if (req.fromDate().isBefore(today.minusDays(MAX_BACKDATE_DAYS))) {
            throw BusinessException.validation("Leave cannot be back-dated by more than " + MAX_BACKDATE_DAYS + " days.",
                    "fromDate");
        }
        if (req.toDate().isAfter(today.plusYears(1))) {
            throw BusinessException.validation("Leave cannot be applied more than a year ahead.", "toDate");
        }
        if (req.fromDate().getYear() != req.toDate().getYear()) {
            throw BusinessException.validation("A request cannot span two calendar years; split it in two.", "toDate");
        }
        if (req.fromDate().plusDays(MAX_SPAN_DAYS).isBefore(req.toDate())) {
            throw BusinessException.validation("A single request can cover at most " + MAX_SPAN_DAYS + " days.", "toDate");
        }

        LeavePolicy policy = policyService.find(tenant, req.policyId());
        if (!policy.isActive()) {
            throw BusinessException.validation("This leave type is not available.", "policyId");
        }
        boolean halfDay = req.dayType() != DayType.FULL_DAY;
        if (halfDay && !policy.isHalfDayAllowed()) {
            throw BusinessException.validation(policy.getName() + " does not allow half-day leave.", "dayType");
        }
        if (halfDay && !req.fromDate().equals(req.toDate())) {
            throw BusinessException.validation("Half-day leave must be for a single date.", "dayType");
        }

        long workingDays = workingDays(tenant, req.fromDate(), req.toDate());
        if (workingDays == 0) {
            throw BusinessException.validation("The selected dates are all Sundays or holidays.", "fromDate");
        }
        BigDecimal days = halfDay ? new BigDecimal("0.5") : BigDecimal.valueOf(workingDays);

        BigDecimal used = used(tenant, user.userId(), policy.getId(), req.fromDate().getYear());
        if (used.add(days).compareTo(policy.getAnnualDays()) > 0) {
            throw BusinessException.rule("Not enough " + policy.getName() + " balance. Remaining "
                    + policy.getAnnualDays().subtract(used).max(BigDecimal.ZERO) + " day(s), requested " + days + ".");
        }
        if (leaves.countOverlapping(tenant, user.userId(), CHARGED, req.fromDate(), req.toDate()) > 0) {
            throw BusinessException.conflict("You already have a leave request overlapping these dates.", "fromDate");
        }

        LeaveRequest l = new LeaveRequest();
        l.setTenantCode(tenant);
        l.setSubject(Subject.of(user));
        l.setPolicyId(policy.getId());
        l.setDayType(req.dayType());
        l.setFromDate(req.fromDate());
        l.setToDate(req.toDate());
        l.setDays(days);
        l.setReason(req.reason().trim());
        l.setStatus(RequestStatus.PENDING);
        leaves.save(l);
        audit.record("leaves", "Applied leave", policy.getCode() + " " + days + " day(s), " + req.fromDate() + " to " + req.toDate());
        return LeaveDto.Response.from(l, policy);
    }

    @Transactional
    public LeaveDto.Response decide(UUID id, DecisionRequest decision) {
        AuthenticatedUser approver = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        LeaveRequest l = find(tenant, id);
        if (l.getStatus() != RequestStatus.PENDING) {
            throw BusinessException.rule("This request was already " + l.getStatus().name().toLowerCase() + ".");
        }
        Subject s = l.getSubject();
        DataScope.assertCanDecide(approver, s.getUserId(), s.getBranchId(), s.getEmployeeRole());
        LeavePolicy policy = policyService.find(tenant, l.getPolicyId());

        boolean approved = decision.approved();
        l.setStatus(approved ? RequestStatus.APPROVED : RequestStatus.REJECTED);
        l.setDecidedBy(approver.userId());
        l.setDecidedAt(Instant.now(clock));
        l.setDecisionNote(decision.note());
        if (approved) {
            markAttendance(tenant, l, policy);
        }
        audit.record("leaves", approved ? "Approved leave" : "Rejected leave",
                s.getEmployeeName() + " - " + policy.getCode() + " " + l.getDays() + " day(s)");
        return LeaveDto.Response.from(l, policy);
    }

    @Transactional
    public void cancel(UUID id) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        LeaveRequest l = find(SecurityUtils.currentTenant(), id);
        if (!l.getSubject().getUserId().equals(user.userId())) {
            throw BusinessException.notFound("Leave request");
        }
        if (l.getStatus() != RequestStatus.PENDING) {
            throw BusinessException.rule("Only pending requests can be cancelled.");
        }
        l.setStatus(RequestStatus.CANCELLED);
        audit.record("leaves", "Cancelled leave", l.getFromDate() + " to " + l.getToDate());
    }

    /** Writes leave days into attendance, without overwriting days the employee actually worked. */
    private void markAttendance(String tenant, LeaveRequest l, LeavePolicy policy) {
        Set<LocalDate> closures = holidayService.fullDayClosures(tenant, l.getFromDate(), l.getToDate());
        AttendanceStatus status = l.getDayType() != DayType.FULL_DAY ? AttendanceStatus.HALF_DAY_LEAVE
                : policy.isPaid() ? AttendanceStatus.ON_LEAVE : AttendanceStatus.LWP;
        for (LocalDate d = l.getFromDate(); !d.isAfter(l.getToDate()); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY || closures.contains(d)) {
                continue;
            }
            LocalDate date = d;
            AttendanceRecord r = records.findByTenantCodeAndSubjectUserIdAndWorkDate(tenant, l.getSubject().getUserId(), date)
                    .orElseGet(() -> {
                        AttendanceRecord created = new AttendanceRecord();
                        created.setTenantCode(tenant);
                        created.setSubject(l.getSubject().copy());
                        created.setWorkDate(date);
                        return created;
                    });
            if (r.getCheckIn() != null && r.getCheckOut() != null) {
                continue;
            }
            r.setStatus(status);
            r.setNote(policy.getName() + (policy.isPaid() ? "" : " (unpaid)"));
            records.save(r);
        }
    }

    private long workingDays(String tenant, LocalDate from, LocalDate to) {
        Set<LocalDate> closures = holidayService.fullDayClosures(tenant, from, to);
        return from.datesUntil(to.plusDays(1))
                .filter(d -> d.getDayOfWeek() != DayOfWeek.SUNDAY && !closures.contains(d))
                .count();
    }

    private BigDecimal used(String tenant, UUID userId, UUID policyId, int year) {
        return leaves.sumDays(tenant, userId, policyId, CHARGED, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    private LeaveRequest find(String tenant, UUID id) {
        return leaves.findByIdAndTenantCode(id, tenant).orElseThrow(() -> BusinessException.notFound("Leave request"));
    }

    private Map<UUID, LeavePolicy> policyMap(String tenant) {
        return policyService.policiesFor(tenant).stream().collect(Collectors.toMap(LeavePolicy::getId, Function.identity()));
    }
}

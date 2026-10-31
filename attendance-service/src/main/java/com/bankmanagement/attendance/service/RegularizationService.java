package com.bankmanagement.attendance.service;

import com.bankmanagement.attendance.domain.AttendanceRecord;
import com.bankmanagement.attendance.domain.Enums.AttendanceSource;
import com.bankmanagement.attendance.domain.Enums.AttendanceStatus;
import com.bankmanagement.attendance.domain.Enums.RequestStatus;
import com.bankmanagement.attendance.domain.Regularization;
import com.bankmanagement.attendance.domain.Subject;
import com.bankmanagement.attendance.dto.AttendanceDto;
import com.bankmanagement.attendance.dto.DecisionRequest;
import com.bankmanagement.attendance.repository.AttendanceRecordRepository;
import com.bankmanagement.attendance.repository.RegularizationRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Attendance corrections: employee requests, a senior in scope approves. */
@Service
@RequiredArgsConstructor
public class RegularizationService {

    private static final int MAX_DAYS_BACK = 30;

    private final RegularizationRepository regularizations;
    private final AttendanceRecordRepository records;
    private final AuditPublisher audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AttendanceDto.CorrectionResponse> list() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return regularizations.findAll(ScopeSpecs.inScope(SecurityUtils.currentTenant(), DataScope.of(user)),
                        Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().map(AttendanceDto.CorrectionResponse::from).toList();
    }

    @Transactional
    public AttendanceDto.CorrectionResponse create(AttendanceDto.CorrectionRequest req) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        LocalDate today = LocalDate.now(clock);
        if (req.workDate().isAfter(today)) {
            throw BusinessException.validation("You cannot request a correction for a future date.", "workDate");
        }
        if (req.workDate().isBefore(today.minusDays(MAX_DAYS_BACK))) {
            throw BusinessException.validation("Corrections can only be requested for the last " + MAX_DAYS_BACK + " days.",
                    "workDate");
        }
        if (!req.checkOut().isAfter(req.checkIn())) {
            throw BusinessException.validation("Out time must be after in time.", "checkOut");
        }
        if (regularizations.existsByTenantCodeAndSubjectUserIdAndWorkDateAndStatus(tenant, user.userId(),
                req.workDate(), RequestStatus.PENDING)) {
            throw BusinessException.conflict("You already have a pending correction for this date.", "workDate");
        }
        Regularization r = new Regularization();
        r.setTenantCode(tenant);
        r.setSubject(Subject.of(user));
        r.setWorkDate(req.workDate());
        r.setCheckIn(req.checkIn());
        r.setCheckOut(req.checkOut());
        r.setReason(req.reason().trim());
        r.setStatus(RequestStatus.PENDING);
        regularizations.save(r);
        audit.record("regs", "Requested attendance correction", req.workDate().toString());
        return AttendanceDto.CorrectionResponse.from(r);
    }

    @Transactional
    public AttendanceDto.CorrectionResponse decide(UUID id, DecisionRequest decision) {
        AuthenticatedUser approver = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        Regularization r = regularizations.findByIdAndTenantCode(id, tenant)
                .orElseThrow(() -> BusinessException.notFound("Correction request"));
        if (r.getStatus() != RequestStatus.PENDING) {
            throw BusinessException.rule("This request was already " + r.getStatus().name().toLowerCase() + ".");
        }
        Subject s = r.getSubject();
        DataScope.assertCanDecide(approver, s.getUserId(), s.getBranchId(), s.getEmployeeRole());

        boolean approved = decision.approved();
        r.setStatus(approved ? RequestStatus.APPROVED : RequestStatus.REJECTED);
        r.setDecidedBy(approver.userId());
        r.setDecidedAt(Instant.now(clock));
        r.setDecisionNote(decision.note());

        if (approved) {
            AttendanceRecord record = records.findByTenantCodeAndSubjectUserIdAndWorkDate(tenant, s.getUserId(),
                    r.getWorkDate()).orElseGet(() -> {
                AttendanceRecord created = new AttendanceRecord();
                created.setTenantCode(tenant);
                created.setSubject(s.copy());
                created.setWorkDate(r.getWorkDate());
                return created;
            });
            record.setCheckIn(r.getCheckIn());
            record.setCheckOut(r.getCheckOut());
            record.setHoursWorked(AttendanceService.hoursBetween(r.getCheckIn(), r.getCheckOut()));
            record.setStatus(AttendanceStatus.PRESENT);
            record.setNote("Corrected via approved request");
            record.setSource(AttendanceSource.CORRECTION);
            records.save(record);
        }
        audit.record("regs", approved ? "Approved correction" : "Rejected correction",
                s.getEmployeeName() + " - " + r.getWorkDate());
        return AttendanceDto.CorrectionResponse.from(r);
    }

    @Transactional
    public void cancel(UUID id) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        Regularization r = regularizations.findByIdAndTenantCode(id, SecurityUtils.currentTenant())
                .filter(x -> x.getSubject().getUserId().equals(user.userId()))
                .orElseThrow(() -> BusinessException.notFound("Correction request"));
        if (r.getStatus() != RequestStatus.PENDING) {
            throw BusinessException.rule("Only pending requests can be cancelled.");
        }
        r.setStatus(RequestStatus.CANCELLED);
        audit.record("regs", "Cancelled correction", r.getWorkDate().toString());
    }
}

package com.bankmanagement.attendance.service;

import com.bankmanagement.attendance.domain.AttendanceRule;
import com.bankmanagement.attendance.dto.AttendanceDto;
import com.bankmanagement.attendance.repository.AttendanceRuleRepository;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttendanceRuleService {

    private final AttendanceRuleRepository rules;
    private final AuditPublisher audit;

    /** Rules of the tenant, or defaults (09:30 start, 15 min grace) if never configured. */
    @Transactional(readOnly = true)
    public AttendanceRule rulesFor(String tenant) {
        return rules.findByTenantCode(tenant).orElseGet(() -> {
            AttendanceRule defaults = new AttendanceRule();
            defaults.setTenantCode(tenant);
            return defaults;
        });
    }

    @Transactional(readOnly = true)
    public AttendanceDto.RuleResponse get() {
        return AttendanceDto.RuleResponse.from(rulesFor(SecurityUtils.currentTenant()));
    }

    @Transactional
    public AttendanceDto.RuleResponse update(AttendanceDto.RuleRequest req) {
        String tenant = SecurityUtils.currentTenant();
        AttendanceRule rule = rules.findByTenantCode(tenant).orElseGet(() -> {
            AttendanceRule r = new AttendanceRule();
            r.setTenantCode(tenant);
            return r;
        });
        rule.setShiftStart(req.shiftStart());
        rule.setGraceMinutes(req.graceMinutes());
        rule.setHalfDayHours(req.halfDayHours());
        rule.setMaxGpsAccuracyMeters(req.maxGpsAccuracyMeters());
        rules.save(rule);
        audit.record("attendance", "Updated attendance rules", "Shift " + req.shiftStart() + ", grace "
                + req.graceMinutes() + " min, half-day below " + req.halfDayHours() + " h");
        return AttendanceDto.RuleResponse.from(rule);
    }
}

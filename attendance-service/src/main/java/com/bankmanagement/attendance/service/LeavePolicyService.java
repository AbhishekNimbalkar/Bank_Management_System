package com.bankmanagement.attendance.service;

import com.bankmanagement.attendance.domain.LeavePolicy;
import com.bankmanagement.attendance.dto.LeaveDto;
import com.bankmanagement.attendance.repository.LeavePolicyRepository;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeavePolicyService {

    private final LeavePolicyRepository policies;
    private final AuditPublisher audit;

    /** Organizations start with the standard Indian bank leave set (CL, SL, PL, LWP). */
    @Transactional
    public List<LeavePolicy> policiesFor(String tenant) {
        if (!policies.existsByTenantCode(tenant)) {
            policies.saveAll(List.of(
                    policy(tenant, "Casual Leave", "CL", 12, false, true, true),
                    policy(tenant, "Sick Leave", "SL", 10, false, true, true),
                    policy(tenant, "Privilege Leave", "PL", 15, true, true, false),
                    policy(tenant, "Leave Without Pay", "LWP", 60, false, false, true)));
        }
        return policies.findByTenantCodeOrderByNameAsc(tenant);
    }

    @Transactional
    public List<LeaveDto.PolicyResponse> list() {
        return policiesFor(SecurityUtils.currentTenant()).stream().map(LeaveDto.PolicyResponse::from).toList();
    }

    @Transactional
    public LeaveDto.PolicyResponse create(LeaveDto.PolicyRequest req) {
        String tenant = SecurityUtils.currentTenant();
        policiesFor(tenant);
        if (policies.existsByTenantCodeAndCode(tenant, req.code())) {
            throw BusinessException.conflict("Policy code " + req.code() + " already exists.", "code");
        }
        LeavePolicy p = new LeavePolicy();
        p.setTenantCode(tenant);
        p.setCode(req.code());
        apply(p, req);
        policies.save(p);
        audit.record("policies", "Created leave policy", p.getName() + " (" + p.getCode() + ", " + p.getAnnualDays() + " days)");
        return LeaveDto.PolicyResponse.from(p);
    }

    @Transactional
    public LeaveDto.PolicyResponse update(UUID id, LeaveDto.PolicyRequest req) {
        LeavePolicy p = find(SecurityUtils.currentTenant(), id);
        apply(p, req);
        audit.record("policies", "Updated leave policy", p.getName() + " (" + p.getCode() + ", " + p.getAnnualDays() + " days)");
        return LeaveDto.PolicyResponse.from(p);
    }

    LeavePolicy find(String tenant, UUID id) {
        return policies.findByIdAndTenantCode(id, tenant).orElseThrow(() -> BusinessException.notFound("Leave policy"));
    }

    private static void apply(LeavePolicy p, LeaveDto.PolicyRequest req) {
        p.setName(req.name().trim());
        p.setAnnualDays(req.annualDays());
        p.setCarryForward(req.carryForward());
        p.setPaid(req.paid());
        p.setHalfDayAllowed(req.halfDayAllowed());
        p.setActive(req.active());
    }

    private static LeavePolicy policy(String tenant, String name, String code, int days, boolean carry, boolean paid,
                                      boolean halfDay) {
        LeavePolicy p = new LeavePolicy();
        p.setTenantCode(tenant);
        p.setName(name);
        p.setCode(code);
        p.setAnnualDays(BigDecimal.valueOf(days));
        p.setCarryForward(carry);
        p.setPaid(paid);
        p.setHalfDayAllowed(halfDay);
        p.setActive(true);
        return p;
    }
}

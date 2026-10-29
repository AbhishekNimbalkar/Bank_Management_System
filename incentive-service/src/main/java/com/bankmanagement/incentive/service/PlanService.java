package com.bankmanagement.incentive.service;

import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.incentive.domain.Enums.PlanStatus;
import com.bankmanagement.incentive.domain.IncentivePlan;
import com.bankmanagement.incentive.dto.IncentiveDto;
import com.bankmanagement.incentive.repository.IncentivePlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final IncentivePlanRepository plans;
    private final EntitlementGuard entitlementGuard;
    private final AuditPublisher audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<IncentiveDto.PlanResponse> list(String month) {
        String tenant = SecurityUtils.currentTenant();
        List<IncentivePlan> rows = month == null ? plans.findByTenantCodeOrderByMonthDescNameAsc(tenant)
                : plans.findByTenantCodeAndMonthOrderByNameAsc(tenant, month);
        return rows.stream().map(IncentiveDto.PlanResponse::from).toList();
    }

    @Transactional
    public IncentiveDto.PlanResponse create(IncentiveDto.PlanRequest req) {
        entitlementGuard.requireIncentives();
        String tenant = SecurityUtils.currentTenant();
        if (YearMonth.parse(req.month()).isBefore(YearMonth.now(clock))) {
            throw BusinessException.validation("Plans cannot be created for a past month.", "month");
        }
        String name = req.name().trim();
        if (plans.existsByTenantCodeAndMonthAndNameIgnoreCase(tenant, req.month(), name)) {
            throw BusinessException.conflict("A plan named '" + name + "' already exists for " + req.month() + ".", "name");
        }
        IncentivePlan p = new IncentivePlan();
        p.setTenantCode(tenant);
        p.setMonth(req.month());
        apply(p, req, name);
        plans.save(p);
        audit.record("plans", "Created incentive plan", describe(p));
        return IncentiveDto.PlanResponse.from(p);
    }

    /** Past months are frozen so paid-out incentives can always be reproduced. */
    @Transactional
    public IncentiveDto.PlanResponse update(UUID id, IncentiveDto.PlanRequest req) {
        String tenant = SecurityUtils.currentTenant();
        IncentivePlan p = find(tenant, id);
        if (YearMonth.parse(p.getMonth()).isBefore(YearMonth.now(clock))) {
            throw BusinessException.rule("Plans of past months are closed and cannot be changed.");
        }
        if (!p.getMonth().equals(req.month())) {
            throw BusinessException.rule("A plan's month cannot change; create a new plan instead.");
        }
        apply(p, req, req.name().trim());
        audit.record("plans", "Updated incentive plan", describe(p));
        return IncentiveDto.PlanResponse.from(p);
    }

    IncentivePlan find(String tenant, UUID id) {
        return plans.findByIdAndTenantCode(id, tenant).orElseThrow(() -> BusinessException.notFound("Incentive plan"));
    }

    private static void apply(IncentivePlan p, IncentiveDto.PlanRequest req, String name) {
        p.setName(name);
        p.setCategory(req.category());
        p.setTarget(req.target());
        p.setThresholdPercent(req.thresholdPercent());
        p.setBaseRatePercent(req.baseRatePercent());
        p.setBonusRatePercent(req.bonusRatePercent());
        p.setStatus(req.status() == null ? PlanStatus.ACTIVE : req.status());
    }

    private static String describe(IncentivePlan p) {
        return p.getName() + " " + p.getMonth() + ": target " + p.getTarget() + ", threshold " + p.getThresholdPercent()
                + "%, base " + p.getBaseRatePercent() + "%, bonus " + p.getBonusRatePercent() + "%";
    }
}

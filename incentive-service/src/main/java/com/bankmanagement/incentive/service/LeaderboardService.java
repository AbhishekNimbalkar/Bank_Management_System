package com.bankmanagement.incentive.service;

import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.DataScope;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.common.web.CsvWriter;
import com.bankmanagement.incentive.domain.Customer;
import com.bankmanagement.incentive.domain.Enums.PlanStatus;
import com.bankmanagement.incentive.domain.IncentivePlan;
import com.bankmanagement.incentive.domain.Owner;
import com.bankmanagement.incentive.dto.IncentiveDto;
import com.bankmanagement.incentive.repository.CustomerRepository;
import com.bankmanagement.incentive.repository.IncentivePlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Monthly ranking by converted volume, with incentive computed against the selected plan. */
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final CustomerRepository customers;
    private final IncentivePlanRepository plans;
    private final PlanService planService;
    private final AuditPublisher audit;

    @Transactional(readOnly = true)
    public IncentiveDto.Leaderboard leaderboard(String month, UUID planId) {
        String tenant = SecurityUtils.currentTenant();
        AuthenticatedUser viewer = SecurityUtils.currentUser();
        DataScope scope = DataScope.of(viewer);
        YearMonth ym = YearMonth.parse(month);

        IncentivePlan plan = resolvePlan(tenant, month, planId);
        List<Customer> conversions = customers.conversionsBetween(tenant, ym.atDay(1), ym.atEndOfMonth()).stream()
                .filter(c -> plan == null || plan.getCategory().covers(c.getProductType()))
                .toList();

        Map<UUID, Totals> byOwner = new LinkedHashMap<>();
        for (Customer c : conversions) {
            byOwner.computeIfAbsent(c.getOwner().getUserId(), k -> new Totals(c.getOwner())).add(c.getAmount());
        }
        List<Totals> sorted = new ArrayList<>(byOwner.values());
        sorted.sort(Comparator.comparing(Totals::achieved).reversed().thenComparing(t -> t.owner.getName()));

        List<IncentiveDto.Ranking> rankings = new ArrayList<>();
        int rank = 0;
        for (Totals t : sorted) {
            rank++;
            Owner o = t.owner;
            boolean visiblePay = scope.includes(o.getBranchId(), o.getUserId());
            if (plan == null) {
                rankings.add(new IncentiveDto.Ranking(rank, o.getUserId(), o.getName(), o.getCode(), o.getDesignation(),
                        o.getBranchId(), t.deals, t.achieved, null, 0, false, null));
                continue;
            }
            IncentiveCalculator.Result r = IncentiveCalculator.calculate(t.achieved, plan.getTarget(),
                    plan.getThresholdPercent(), plan.getBaseRatePercent(), plan.getBonusRatePercent());
            rankings.add(new IncentiveDto.Ranking(rank, o.getUserId(), o.getName(), o.getCode(), o.getDesignation(),
                    o.getBranchId(), t.deals, t.achieved, plan.getTarget(), r.achievementPercent(), r.qualified(),
                    visiblePay ? r.incentive() : null));
        }
        return new IncentiveDto.Leaderboard(month, plan == null ? null : IncentiveDto.PlanResponse.from(plan), rankings);
    }

    /** Incentive statement for payroll: only rows within the caller's data scope. */
    @Transactional(readOnly = true)
    public byte[] statementCsv(String month, UUID planId) {
        AuthenticatedUser viewer = SecurityUtils.currentUser();
        DataScope scope = DataScope.of(viewer);
        IncentiveDto.Leaderboard board = leaderboard(month, planId);
        if (board.plan() == null) {
            throw BusinessException.rule("There is no active incentive plan for " + month + ".");
        }
        List<IncentiveDto.Ranking> rows = board.rankings().stream()
                .filter(r -> scope.includes(r.branchId(), r.userId())).toList();
        List<Function<IncentiveDto.Ranking, Object>> columns = List.of(IncentiveDto.Ranking::rank,
                IncentiveDto.Ranking::name, IncentiveDto.Ranking::code, IncentiveDto.Ranking::deals,
                IncentiveDto.Ranking::achieved, IncentiveDto.Ranking::target, IncentiveDto.Ranking::achievementPercent,
                r -> r.qualified() ? "Yes" : "No", IncentiveDto.Ranking::incentive);
        audit.record("leaderboard", "Exported incentive statement", board.plan().name() + " " + month
                + " (" + rows.size() + " rows)");
        return CsvWriter.write(List.of("Rank", "Employee", "Code", "Deals", "Achieved (INR)", "Target (INR)",
                "Achievement %", "Qualified", "Incentive (INR)"), rows, columns);
    }

    private IncentivePlan resolvePlan(String tenant, String month, UUID planId) {
        if (planId != null) {
            IncentivePlan plan = planService.find(tenant, planId);
            if (!plan.getMonth().equals(month)) {
                throw BusinessException.validation("The selected plan belongs to " + plan.getMonth() + ".", "planId");
            }
            return plan;
        }
        return plans.findByTenantCodeAndMonthAndStatusOrderByNameAsc(tenant, month, PlanStatus.ACTIVE).stream()
                .findFirst().orElse(null);
    }

    private static final class Totals {
        private final Owner owner;
        private BigDecimal achieved = BigDecimal.ZERO;
        private long deals;

        private Totals(Owner owner) {
            this.owner = owner;
        }

        private void add(BigDecimal amount) {
            achieved = achieved.add(amount);
            deals++;
        }

        private BigDecimal achieved() {
            return achieved;
        }
    }
}

package com.bankmanagement.incentive.service;

import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.DataScope;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.incentive.domain.Customer;
import com.bankmanagement.incentive.domain.Enums.CustomerStatus;
import com.bankmanagement.incentive.dto.IncentiveDto;
import com.bankmanagement.incentive.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;

/** Incentive part of the dashboard: my progress on this month's plan and leads awaiting my approval. */
@Service
@RequiredArgsConstructor
public class IncentiveDashboardService {

    private final LeaderboardService leaderboardService;
    private final CustomerRepository customers;
    private final Clock clock;

    public record Summary(String month, IncentiveDto.PlanResponse plan, Integer myRank, long myDeals,
                          BigDecimal myAchieved, BigDecimal target, int achievementPercent, boolean qualified,
                          BigDecimal myIncentive, long leadsAwaitingApproval) {
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        String month = YearMonth.now(clock).toString();

        IncentiveDto.Leaderboard board = leaderboardService.leaderboard(month, null);
        IncentiveDto.Ranking mine = board.rankings().stream()
                .filter(r -> r.userId().equals(user.userId())).findFirst().orElse(null);

        DataScope scope = DataScope.of(user);
        long awaiting = 0;
        if (scope.level() != DataScope.Level.SELF) {
            Specification<Customer> spec = (root, q, cb) -> {
                var base = cb.and(cb.equal(root.get("tenantCode"), tenant),
                        cb.equal(root.get("status"), CustomerStatus.LEAD),
                        cb.notEqual(root.get("makerId"), user.userId()),
                        cb.notEqual(root.get("owner").get("userId"), user.userId()));
                return scope.level() == DataScope.Level.BRANCH
                        ? cb.and(base, cb.equal(root.get("owner").get("branchId"), scope.branchId())) : base;
            };
            awaiting = customers.count(spec);
        }

        BigDecimal target = board.plan() == null ? null : board.plan().target();
        if (mine == null) {
            return new Summary(month, board.plan(), null, 0, BigDecimal.ZERO, target, 0, false,
                    board.plan() == null ? null : BigDecimal.ZERO, awaiting);
        }
        return new Summary(month, board.plan(), mine.rank(), mine.deals(), mine.achieved(), target,
                mine.achievementPercent(), mine.qualified(), mine.incentive(), awaiting);
    }
}

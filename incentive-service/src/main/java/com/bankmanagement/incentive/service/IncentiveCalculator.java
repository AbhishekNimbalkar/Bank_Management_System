package com.bankmanagement.incentive.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Incentive rule:
 * <ul>
 *   <li>achievement = achieved / target</li>
 *   <li>below the threshold: nothing</li>
 *   <li>otherwise: base rate on all achieved volume, plus bonus rate on volume above 100% of target</li>
 * </ul>
 * Amounts are rounded to whole rupees (half-up).
 */
public final class IncentiveCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private IncentiveCalculator() {
    }

    public record Result(int achievementPercent, boolean qualified, BigDecimal incentive) {
    }

    public static Result calculate(BigDecimal achieved, BigDecimal target, int thresholdPercent,
                                   BigDecimal baseRatePercent, BigDecimal bonusRatePercent) {
        if (target.signum() <= 0) {
            throw new IllegalArgumentException("target must be positive");
        }
        int percent = achieved.multiply(HUNDRED).divide(target, 0, RoundingMode.DOWN).intValue();
        boolean qualified = percent >= thresholdPercent && achieved.signum() > 0;
        if (!qualified) {
            return new Result(percent, false, BigDecimal.ZERO);
        }
        BigDecimal incentive = achieved.multiply(baseRatePercent).divide(HUNDRED, 10, RoundingMode.HALF_UP);
        BigDecimal excess = achieved.subtract(target);
        if (excess.signum() > 0) {
            incentive = incentive.add(excess.multiply(bonusRatePercent).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        }
        return new Result(percent, true, incentive.setScale(0, RoundingMode.HALF_UP));
    }
}

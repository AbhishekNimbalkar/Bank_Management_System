package com.bankmanagement.incentive.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class IncentiveCalculatorTest {

    private static final BigDecimal TARGET = new BigDecimal("250000");
    private static final BigDecimal BASE = new BigDecimal("0.25");
    private static final BigDecimal BONUS = new BigDecimal("0.25");

    private static IncentiveCalculator.Result calc(String achieved) {
        return IncentiveCalculator.calculate(new BigDecimal(achieved), TARGET, 70, BASE, BONUS);
    }

    @Test
    void belowThresholdEarnsNothing() {
        IncentiveCalculator.Result r = calc("150000"); // 60%
        assertThat(r.achievementPercent()).isEqualTo(60);
        assertThat(r.qualified()).isFalse();
        assertThat(r.incentive()).isEqualByComparingTo("0");
    }

    @Test
    void atThresholdEarnsBaseRateOnAllVolume() {
        IncentiveCalculator.Result r = calc("175000"); // exactly 70%
        assertThat(r.qualified()).isTrue();
        assertThat(r.incentive()).isEqualByComparingTo("438"); // 175000 * 0.25% = 437.5 -> 438
    }

    @Test
    void aboveTargetAddsBonusOnExcess() {
        IncentiveCalculator.Result r = calc("2800000");
        // base 2,800,000 * 0.25% = 7000; bonus (2,800,000 - 250,000) * 0.25% = 6375
        assertThat(r.incentive()).isEqualByComparingTo("13375");
        assertThat(r.achievementPercent()).isEqualTo(1120);
    }

    @Test
    void zeroAchievedNeverQualifiesEvenWithZeroThreshold() {
        IncentiveCalculator.Result r = IncentiveCalculator.calculate(BigDecimal.ZERO, TARGET, 0, BASE, BONUS);
        assertThat(r.qualified()).isFalse();
    }
}

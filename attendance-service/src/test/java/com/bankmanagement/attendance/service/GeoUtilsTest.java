package com.bankmanagement.attendance.service;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class GeoUtilsTest {

    @Test
    void zeroDistanceForSamePoint() {
        assertThat(GeoUtils.distanceMeters(18.5204, 73.8567, 18.5204, 73.8567)).isZero();
    }

    @Test
    void roughly20MetersForSmallOffset() {
        int d = GeoUtils.distanceMeters(18.5204, 73.8567, 18.52055, 73.85685);
        assertThat(d).isBetween(15, 30);
    }

    @Test
    void puneToMumbaiIsAbout120Km() {
        int d = GeoUtils.distanceMeters(18.5204, 73.8567, 19.0760, 72.8777);
        assertThat(d).isBetween(115_000, 125_000);
    }

    @Test
    void hoursBetweenRoundsToTwoDecimals() {
        assertThat(AttendanceService.hoursBetween(LocalTime.of(9, 20), LocalTime.of(17, 45))).isEqualByComparingTo("8.42");
        assertThat(AttendanceService.hoursBetween(LocalTime.of(10, 0), LocalTime.of(9, 0))).isEqualByComparingTo("0");
    }
}

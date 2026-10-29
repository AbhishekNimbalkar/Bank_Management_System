package com.bankmanagement.attendance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Business dates and shift times are evaluated in the bank's local time zone, never the server's. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock businessClock(@Value("${app.timezone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}

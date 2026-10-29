package com.bankmanagement.incentive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Business months and dates are evaluated in the bank's local time zone. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock businessClock(@Value("${app.timezone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}

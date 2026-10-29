package com.bankmanagement.auth.service;

import com.bankmanagement.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokens;

    /** Keeps expired tokens for 7 days (forensics), then purges them. Runs daily at 02:30. */
    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = refreshTokens.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(7)));
        log.info("Purged {} expired refresh tokens", removed);
    }
}

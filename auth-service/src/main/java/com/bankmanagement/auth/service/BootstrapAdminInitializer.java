package com.bankmanagement.auth.service;

import com.bankmanagement.auth.config.AuthProperties;
import com.bankmanagement.auth.domain.UserAccount;
import com.bankmanagement.auth.domain.UserStatus;
import com.bankmanagement.auth.repository.UserAccountRepository;
import com.bankmanagement.common.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creates the first platform owner from environment variables when none exists. No credential is
 * ever hard-coded; the account must change its password at first sign-in.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.SSA.name())) {
            return;
        }
        AuthProperties.Bootstrap boot = props.bootstrap();
        if (boot == null || boot.username() == null || boot.username().isBlank()
                || boot.password() == null || boot.password().isBlank()) {
            log.warn("No platform owner exists and BOOTSTRAP_ADMIN_USERNAME/PASSWORD are not set - skipping bootstrap.");
            return;
        }
        String username = boot.username().trim().toLowerCase(Locale.ROOT);
        String name = boot.name() == null || boot.name().isBlank() ? "Platform Owner" : boot.name();
        passwordPolicy.validate(boot.password(), username, name);

        UserAccount owner = new UserAccount();
        owner.setTenantCode(Role.PLATFORM_TENANT);
        owner.setUsername(username);
        owner.setDisplayName(name);
        owner.setEmail(boot.email());
        owner.setRole(Role.SSA.name());
        owner.setPasswordHash(passwordEncoder.encode(boot.password()));
        owner.setStatus(UserStatus.ACTIVE);
        owner.setMustChangePassword(true);
        users.save(owner);
        log.info("Bootstrap platform owner '{}' created (tenant '{}'); password change required at first sign-in.",
                username, Role.PLATFORM_TENANT);
    }
}

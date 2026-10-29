package com.bankmanagement.auth.service;

import com.bankmanagement.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        assertThat(policy.violations("Kx9#mPq2!vLr", "rohit.b", "Rohit Bhosale")).isEmpty();
    }

    @Test
    void rejectsShortAndSimplePasswords() {
        assertThat(policy.violations("abc", "user", "User"))
                .contains("at least 10 characters", "an upper-case letter", "a number", "a symbol");
    }

    @Test
    void rejectsUsernameNameAndCommonWords() {
        assertThat(policy.violations("Rohit.b@2026xY", "rohit.b", "Rohit Bhosale"))
                .contains("no part of your username");
        assertThat(policy.violations("Password@2026", "someone", "Some One"))
                .contains("no common words (password, welcome, admin...)");
        assertThat(policy.violations("Sneha#2026Kp", "sg01", "Sneha Gaikwad")).contains("not your name");
    }

    @Test
    void validateThrowsBusinessException() {
        assertThatThrownBy(() -> policy.validate("weak", "u", "N")).isInstanceOf(BusinessException.class);
    }

    @Test
    void generatedPasswordsAlwaysSatisfyPolicy() {
        PasswordGenerator generator = new PasswordGenerator();
        for (int i = 0; i < 200; i++) {
            assertThat(policy.violations(generator.generate(), "employee", "Pooja Jadhav")).isEmpty();
        }
    }
}

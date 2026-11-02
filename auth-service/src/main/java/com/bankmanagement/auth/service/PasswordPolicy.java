package com.bankmanagement.auth.service;

import com.bankmanagement.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Organization-wide password rules (aligned with NIST 800-63B plus bank-style complexity). */
@Component
public class PasswordPolicy {

    public static final String DESCRIPTION =
            "At least 10 characters with upper & lower case letters, a number and a symbol.";

    private static final List<String> BANNED_WORDS = List.of(
            "password", "welcome", "admin", "qwerty", "letmein", "12345", "bank", "iloveyou", "abc123");

    public List<String> violations(String password, String username, String displayName) {
        String value = password == null ? "" : password;
        List<String> problems = new ArrayList<>();
        if (value.length() < 10) {
            problems.add("at least 10 characters");
        }
        if (value.length() > 128) {
            problems.add("at most 128 characters");
        }
        if (!value.matches(".*[a-z].*")) {
            problems.add("a lower-case letter");
        }
        if (!value.matches(".*[A-Z].*")) {
            problems.add("an upper-case letter");
        }
        if (!value.matches(".*\\d.*")) {
            problems.add("a number");
        }
        if (!value.matches(".*[^A-Za-z0-9].*")) {
            problems.add("a symbol");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (username != null && username.length() >= 3 && lower.contains(username.toLowerCase(Locale.ROOT))) {
            problems.add("no part of your username");
        }
        if (BANNED_WORDS.stream().anyMatch(lower::contains)) {
            problems.add("no common words (password, welcome, admin...)");
        }
        if (displayName != null) {
            String firstName = displayName.trim().split("\\s+")[0];
            if (firstName.length() >= 4 && lower.contains(firstName.toLowerCase(Locale.ROOT))) {
                problems.add("not your name");
            }
        }
        return problems;
    }

    public void validate(String password, String username, String displayName) {
        List<String> problems = violations(password, username, displayName);
        if (!problems.isEmpty()) {
            throw BusinessException.validation("Password must contain: " + String.join(", ", problems) + ".",
                    "password");
        }
    }
}

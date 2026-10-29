package com.bankmanagement.organization.service;

import java.util.Locale;
import java.util.function.Predicate;

/** Generates bank codes and IFSC-format codes for auto-created headquarters branches. */
final class CodeGenerator {

    private CodeGenerator() {
    }

    /** Initials of the name (e.g. "Sahyadri Co-operative Bank" -> "SCB"), made unique with a digit. */
    static String bankCode(String name, Predicate<String> taken) {
        StringBuilder initials = new StringBuilder();
        for (String word : name.split("[^A-Za-z0-9]+")) {
            if (!word.isEmpty()) {
                initials.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        String base = initials.length() >= 2 ? initials.substring(0, Math.min(4, initials.length()))
                : (name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT) + "BNK").substring(0, 3);
        String code = base;
        for (int i = 1; taken.test(code); i++) {
            code = base + i;
        }
        return code;
    }

    /** IFSC = 4 letters + '0' + 6 alphanumerics. Letters are derived from the bank code. */
    static String ifsc(String bankCode, Predicate<String> taken) {
        String letters = (bankCode.replaceAll("[^A-Z]", "") + "XXXX").substring(0, 4);
        for (int n = 1; n < 1_000_000; n++) {
            String candidate = letters + "0" + String.format("%06d", n);
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("IFSC space exhausted for " + letters);
    }

    static String employeePrefix(String tenantCode) {
        return tenantCode.substring(0, 2).toUpperCase(Locale.ROOT);
    }
}

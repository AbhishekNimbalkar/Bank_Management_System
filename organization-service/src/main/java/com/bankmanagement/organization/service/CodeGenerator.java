package com.bankmanagement.organization.service;

import java.util.Locale;
import java.util.function.Predicate;

/** Generates bank and employee codes. */
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

    static String employeePrefix(String tenantCode) {
        return tenantCode.substring(0, 2).toUpperCase(Locale.ROOT);
    }
}

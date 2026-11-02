package com.bankmanagement.auth.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Generates one-time passwords that always satisfy {@link PasswordPolicy}. */
@Component
public class PasswordGenerator {

    private static final String[] CLASSES = {
            "ABCDEFGHJKLMNPQRSTUVWXYZ", "abcdefghijkmnpqrstuvwxyz", "23456789", "@#$%&*!?"
    };
    private static final String ALL = String.join("", CLASSES);
    private static final int LENGTH = 14;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        List<Character> chars = new ArrayList<>(LENGTH);
        for (String set : CLASSES) {
            chars.add(set.charAt(random.nextInt(set.length())));
        }
        while (chars.size() < LENGTH) {
            chars.add(ALL.charAt(random.nextInt(ALL.length())));
        }
        Collections.shuffle(chars, random);
        StringBuilder sb = new StringBuilder(LENGTH);
        chars.forEach(sb::append);
        return sb.toString();
    }
}

package com.bankmanagement.audit.service;

import com.bankmanagement.audit.domain.AuditLog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** SHA-256 over the previous hash and every business field of an entry. */
public final class AuditHasher {

    private static final String SEPARATOR = "␟";

    private AuditHasher() {
    }

    public static String hash(AuditLog e) {
        String material = Stream.of(e.getPrevHash(), e.getTenantCode(), e.getSequence(), e.getId(),
                        e.getOccurredAt(), e.getActorId(), e.getActorRole(), e.getImpersonatorId(), e.getModule(),
                        e.getAction(), e.getDetail(), e.getIpAddress())
                .map(v -> Objects.toString(v, ""))
                .collect(Collectors.joining(SEPARATOR));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

package com.bankmanagement.audit.service;

import com.bankmanagement.audit.domain.AuditLog;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHasherTest {

    private static AuditLog entry() {
        AuditLog e = new AuditLog();
        e.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        e.setTenantCode("sahyadri");
        e.setSequence(1);
        e.setOccurredAt(Instant.parse("2026-01-01T10:00:00Z"));
        e.setActorId("u1");
        e.setActorRole("HR");
        e.setModule("leaves");
        e.setAction("Approved leave");
        e.setDetail("Pooja (2 days)");
        e.setPrevHash("GENESIS");
        return e;
    }

    @Test
    void hashIsDeterministic() {
        assertThat(AuditHasher.hash(entry())).isEqualTo(AuditHasher.hash(entry())).hasSize(64);
    }

    @Test
    void anyFieldChangeChangesHash() {
        String original = AuditHasher.hash(entry());
        AuditLog tampered = entry();
        tampered.setDetail("Pooja (20 days)");
        assertThat(AuditHasher.hash(tampered)).isNotEqualTo(original);

        AuditLog relinked = entry();
        relinked.setPrevHash("abc");
        assertThat(AuditHasher.hash(relinked)).isNotEqualTo(original);
    }
}

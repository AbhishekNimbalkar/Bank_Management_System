package com.bankmanagement.audit.dto;

import com.bankmanagement.audit.domain.AuditLog;

import java.time.Instant;
import java.util.UUID;

public final class AuditDto {

    private AuditDto() {
    }

    public record Filter(String module, String search, Instant from, Instant to) {
    }

    public record Entry(UUID id, long sequence, Instant occurredAt, String actorId, String actorName, String actorRole,
                        String impersonatorId, String module, String action, String detail, String ipAddress,
                        String requestId, String sourceService, String hash) {

        public static Entry from(AuditLog e) {
            return new Entry(e.getId(), e.getSequence(), e.getOccurredAt(), e.getActorId(), e.getActorName(),
                    e.getActorRole(), e.getImpersonatorId(), e.getModule(), e.getAction(), e.getDetail(),
                    e.getIpAddress(), e.getRequestId(), e.getSourceService(), e.getHash());
        }
    }

    /** {@code firstBrokenSequence} is set when the chain was altered. */
    public record Verification(boolean intact, long checkedEntries, Long firstBrokenSequence, Instant verifiedAt) {
    }
}

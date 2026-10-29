package com.bankmanagement.audit.service;

import com.bankmanagement.audit.client.OrganizationClient;
import com.bankmanagement.audit.domain.AuditChainHead;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.Role;
import com.bankmanagement.audit.domain.AuditLog;
import com.bankmanagement.audit.dto.AuditDto;
import com.bankmanagement.audit.repository.AuditLogRepository;
import com.bankmanagement.common.api.PageResponse;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.common.web.CsvWriter;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_EXPORT_ROWS = 50_000;
    private static final int VERIFY_BATCH = 1_000;

    private final AuditLogRepository logs;
    private final AuditPublisher audit;
    private final OrganizationClient organizationClient;

    @Transactional(readOnly = true)
    public PageResponse<AuditDto.Entry> search(AuditDto.Filter filter, int page, int size) {
        return PageResponse.of(logs.findAll(spec(filter), PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "sequence"))), AuditDto.Entry::from);
    }

    /** Recomputes every hash of the tenant's chain and reports the first broken link. */
    @Transactional(readOnly = true)
    public AuditDto.Verification verify() {
        String tenant = SecurityUtils.currentTenant();
        String expectedPrev = AuditChainHead.GENESIS;
        long expectedSeq = 1;
        long checked = 0;
        int page = 0;
        Slice<AuditLog> slice;
        do {
            slice = logs.findByTenantCodeOrderBySequenceAsc(tenant, PageRequest.of(page++, VERIFY_BATCH));
            for (AuditLog e : slice) {
                boolean linkOk = e.getSequence() == expectedSeq && expectedPrev.equals(e.getPrevHash());
                if (!linkOk || !AuditHasher.hash(e).equals(e.getHash())) {
                    return new AuditDto.Verification(false, checked, e.getSequence(), Instant.now());
                }
                expectedPrev = e.getHash();
                expectedSeq++;
                checked++;
            }
        } while (slice.hasNext());
        return new AuditDto.Verification(true, checked, null, Instant.now());
    }

    @Transactional(readOnly = true)
    public byte[] export(AuditDto.Filter filter) {
        if (!Role.PLATFORM_TENANT.equals(SecurityUtils.currentTenant())) {
            OrganizationClient.Entitlements e = organizationClient.entitlements().data();
            if (e.features() == null || !e.features().auditExport()) {
                throw BusinessException.forbidden("Audit log export is not included in your organization's plan.");
            }
        }
        List<AuditLog> rows = logs.findAll(spec(filter),
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.DESC, "sequence"))).getContent();
        List<Function<AuditLog, Object>> columns = List.of(AuditLog::getSequence, AuditLog::getOccurredAt,
                AuditLog::getActorName, AuditLog::getActorRole, AuditLog::getImpersonatorId, AuditLog::getModule,
                AuditLog::getAction, AuditLog::getDetail, AuditLog::getIpAddress, AuditLog::getHash);
        audit.record("logs", "Exported audit log", rows.size() + " entries");
        return CsvWriter.write(List.of("Seq", "Time (UTC)", "Actor", "Role", "Impersonated by", "Module", "Action",
                "Detail", "IP", "Hash"), rows, columns);
    }

    private static Specification<AuditLog> spec(AuditDto.Filter f) {
        String tenant = SecurityUtils.currentTenant();
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("tenantCode"), tenant));
            if (f.module() != null && !f.module().isBlank()) {
                p.add(cb.equal(root.get("module"), f.module()));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThan(root.get("occurredAt"), f.to()));
            }
            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("actorName")), like), cb.like(cb.lower(root.get("action")), like),
                        cb.like(cb.lower(root.get("detail")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}

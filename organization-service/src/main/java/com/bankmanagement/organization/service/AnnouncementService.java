package com.bankmanagement.organization.service;

import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.Role;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.organization.domain.Announcement;
import com.bankmanagement.organization.dto.AnnouncementDto;
import com.bankmanagement.organization.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AnnouncementRepository announcements;
    private final BranchService branchService;
    private final AuditPublisher audit;

    /** Current notices for the caller: organization-wide plus their own branch, pinned first. */
    @Transactional(readOnly = true)
    public List<AnnouncementDto.Response> list() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        LocalDate today = LocalDate.now(IST);
        boolean seesAll = user.isPlatformOwner() || Role.SA.name().equals(user.role()) || Role.HR.name().equals(user.role());

        Specification<Announcement> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("tenantCode"), tenant),
                cb.or(cb.isNull(root.get("expiresOn")), cb.greaterThanOrEqualTo(root.get("expiresOn"), today)),
                seesAll ? cb.conjunction()
                        : user.branchId() == null ? cb.isNull(root.get("branchId"))
                        : cb.or(cb.isNull(root.get("branchId")), cb.equal(root.get("branchId"), user.branchId())));
        return announcements.findAll(spec, Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("publishedOn"),
                Sort.Order.desc("createdAt"))).stream().map(AnnouncementDto.Response::from).toList();
    }

    @Transactional
    public AnnouncementDto.Response create(AnnouncementDto.Request req) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        UUID branchId = resolveBranch(user, tenant, req.branchId());

        Announcement a = new Announcement();
        a.setTenantCode(tenant);
        a.setTitle(req.title().trim());
        a.setBody(req.body().trim());
        a.setPinned(req.pinned());
        a.setBranchId(branchId);
        a.setAuthorId(user.userId());
        a.setAuthorName(user.name());
        a.setPublishedOn(LocalDate.now(IST));
        a.setExpiresOn(req.expiresOn());
        announcements.save(a);
        audit.record("announcements", "Posted announcement", a.getTitle());
        return AnnouncementDto.Response.from(a);
    }

    @Transactional
    public AnnouncementDto.Response update(UUID id, AnnouncementDto.Request req) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        Announcement a = editable(user, tenant, id);
        a.setTitle(req.title().trim());
        a.setBody(req.body().trim());
        a.setPinned(req.pinned());
        a.setBranchId(resolveBranch(user, tenant, req.branchId()));
        a.setExpiresOn(req.expiresOn());
        audit.record("announcements", "Edited announcement", a.getTitle());
        return AnnouncementDto.Response.from(a);
    }

    @Transactional
    public void delete(UUID id) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        Announcement a = editable(user, SecurityUtils.currentTenant(), id);
        announcements.delete(a);
        audit.record("announcements", "Deleted announcement", a.getTitle());
    }

    /** Branch Managers post to their own branch only; others may target any branch or the organization. */
    private UUID resolveBranch(AuthenticatedUser user, String tenant, UUID requested) {
        if (Role.BM.name().equals(user.role())) {
            return user.branchId();
        }
        if (requested != null) {
            branchService.find(tenant, requested);
        }
        return requested;
    }

    /** Authors edit their own notices; SA/HR/platform owner may edit any. */
    private Announcement editable(AuthenticatedUser user, String tenant, UUID id) {
        Announcement a = announcements.findByIdAndTenantCode(id, tenant)
                .orElseThrow(() -> BusinessException.notFound("Announcement"));
        boolean admin = user.isPlatformOwner() || Role.SA.name().equals(user.role()) || Role.HR.name().equals(user.role());
        if (!admin && !Objects.equals(a.getAuthorId(), user.userId())) {
            throw BusinessException.forbidden("You can only change your own announcements.");
        }
        return a;
    }
}

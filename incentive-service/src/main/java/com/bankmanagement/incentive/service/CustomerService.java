package com.bankmanagement.incentive.service;

import com.bankmanagement.common.api.PageResponse;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.exception.ErrorCode;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.DataScope;
import com.bankmanagement.common.security.Role;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.incentive.client.OrganizationClient;
import com.bankmanagement.incentive.domain.Customer;
import com.bankmanagement.incentive.domain.Enums.CustomerStatus;
import com.bankmanagement.incentive.domain.Enums.ProductType;
import com.bankmanagement.incentive.domain.Owner;
import com.bankmanagement.incentive.domain.Product;
import com.bankmanagement.incentive.dto.IncentiveDto;
import com.bankmanagement.incentive.repository.CustomerRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customers;
    private final ProductService productService;
    private final EntitlementGuard entitlementGuard;
    private final OrganizationClient organizationClient;
    private final AuditPublisher audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<IncentiveDto.CustomerResponse> list(CustomerStatus status, ProductType type, String month,
                                                            String search, int page, int size) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        DataScope scope = DataScope.of(user);

        Specification<Customer> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("tenantCode"), tenant));
            if (scope.level() == DataScope.Level.BRANCH) {
                p.add(cb.equal(root.get("owner").get("branchId"), scope.branchId()));
            } else if (scope.level() == DataScope.Level.SELF) {
                p.add(cb.equal(root.get("owner").get("userId"), user.userId()));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                p.add(cb.equal(root.get("productType"), type));
            }
            if (month != null) {
                YearMonth ym = YearMonth.parse(month);
                p.add(cb.between(root.get("leadDate"), ym.atDay(1), ym.atEndOfMonth()));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(root.get("mobile"), like),
                        cb.like(cb.lower(root.get("productName")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        var result = customers.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(result, c -> IncentiveDto.CustomerResponse.from(c, canSeeMobile(user, c)));
    }

    /** Every record starts as a LEAD; conversion needs a checker (see {@link #decide}). */
    @Transactional
    public IncentiveDto.CustomerResponse create(IncentiveDto.CustomerRequest req) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        OrganizationClient.Entitlements entitlements = entitlementGuard.requireIncentives();
        if (customers.countByTenantCode(tenant) >= entitlements.maxCustomers()) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "Customer record limit reached ("
                    + entitlements.maxCustomers() + "). Ask the platform owner to raise your quota.");
        }

        Product product = productService.find(tenant, req.productId());
        if (!product.isActive()) {
            throw BusinessException.validation("This product is not available.", "productId");
        }
        if (customers.existsByTenantCodeAndMobileAndProductIdAndStatusIn(tenant, req.mobile(), product.getId(),
                EnumSet.of(CustomerStatus.LEAD, CustomerStatus.CONVERTED))) {
            throw BusinessException.conflict("This customer already has an open or converted record for "
                    + product.getName() + ".", "mobile");
        }

        Customer c = new Customer();
        c.setTenantCode(tenant);
        c.setName(req.name().trim());
        c.setMobile(req.mobile());
        c.setProductId(product.getId());
        c.setProductName(product.getName());
        c.setProductType(product.getType());
        c.setAmount(req.amount());
        c.setNotes(req.notes());
        c.setOwner(req.ownerEmployeeId() == null ? Owner.of(user) : ownerOnBehalf(user, req.ownerEmployeeId()));
        c.setStatus(CustomerStatus.LEAD);
        c.setLeadDate(LocalDate.now(clock));
        c.setMakerId(user.userId());
        customers.save(c);

        audit.record("customers", "Recorded customer", c.getName() + " - " + c.getProductName() + " Rs " + c.getAmount()
                + " (owner " + c.getOwner().getName() + ")");
        return IncentiveDto.CustomerResponse.from(c, true);
    }

    @Transactional
    public IncentiveDto.CustomerResponse update(UUID id, IncentiveDto.CustomerUpdate req) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        Customer c = find(id);
        requireOwnerOrMaker(user, c);
        if (c.getStatus() != CustomerStatus.LEAD) {
            throw BusinessException.rule("Only open leads can be edited.");
        }
        c.setName(req.name().trim());
        c.setMobile(req.mobile());
        c.setAmount(req.amount());
        c.setNotes(req.notes());
        audit.record("customers", "Edited lead", c.getName() + " - Rs " + c.getAmount());
        return IncentiveDto.CustomerResponse.from(c, true);
    }

    /** Checker step: a senior in scope, who is neither the owner nor the maker. */
    @Transactional
    public IncentiveDto.CustomerResponse decide(UUID id, IncentiveDto.Decision decision) {
        AuthenticatedUser checker = SecurityUtils.currentUser();
        Customer c = find(id);
        if (c.getStatus() != CustomerStatus.LEAD) {
            throw BusinessException.rule("Only open leads can be approved or rejected.");
        }
        if (checker.userId().equals(c.getMakerId())) {
            throw BusinessException.forbidden("You recorded this lead; another manager must approve it.");
        }
        DataScope.assertCanDecide(checker, c.getOwner().getUserId(), c.getOwner().getBranchId(), c.getOwner().getRole());

        boolean approved = decision.approved();
        c.setStatus(approved ? CustomerStatus.CONVERTED : CustomerStatus.REJECTED);
        c.setConvertedDate(approved ? LocalDate.now(clock) : null);
        c.setCheckerId(checker.userId());
        c.setDecidedAt(Instant.now(clock));
        c.setDecisionNote(decision.note());
        audit.record("customers", approved ? "Approved conversion" : "Rejected lead",
                c.getName() + " - " + c.getProductName() + " Rs " + c.getAmount() + " (owner " + c.getOwner().getName() + ")");
        return IncentiveDto.CustomerResponse.from(c, true);
    }

    @Transactional
    public void delete(UUID id) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        Customer c = find(id);
        if (c.getStatus() != CustomerStatus.LEAD) {
            throw BusinessException.rule("Converted or rejected records are kept for audit and cannot be deleted.");
        }
        boolean ownRecord = user.userId().equals(c.getMakerId()) || user.userId().equals(c.getOwner().getUserId());
        if (!ownRecord) {
            DataScope.assertCanDecide(user, c.getOwner().getUserId(), c.getOwner().getBranchId(), c.getOwner().getRole());
        }
        c.markDeleted();
        audit.record("customers", "Deleted lead", c.getName() + " - " + c.getProductName());
    }

    /** A manager credits a junior in scope; the junior's details come from organization-service. */
    private Owner ownerOnBehalf(AuthenticatedUser actor, UUID employeeId) {
        if (actor.rank() <= Role.EMP.rank()) {
            throw BusinessException.forbidden("You can only record business for yourself.");
        }
        OrganizationClient.Employee e = organizationClient.employee(employeeId).data();
        if (e.userId() == null || !"ACTIVE".equals(e.status())) {
            throw BusinessException.validation("Select an active employee.", "ownerEmployeeId");
        }
        if (!DataScope.of(actor).includes(e.branchId(), e.userId())) {
            throw BusinessException.forbidden("This employee is outside your scope.");
        }
        Owner o = new Owner();
        o.setUserId(e.userId());
        o.setName(e.name());
        o.setCode(e.code());
        o.setRole(e.role());
        o.setDesignation(e.designation());
        o.setBranchId(e.branchId());
        return o;
    }

    private Customer find(UUID id) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        Customer c = customers.findByIdAndTenantCode(id, SecurityUtils.currentTenant())
                .orElseThrow(() -> BusinessException.notFound("Customer"));
        boolean inScope = DataScope.of(user).includes(c.getOwner().getBranchId(), c.getOwner().getUserId())
                || user.userId().equals(c.getMakerId());
        if (!inScope) {
            throw BusinessException.notFound("Customer");
        }
        return c;
    }

    private static void requireOwnerOrMaker(AuthenticatedUser user, Customer c) {
        if (!user.userId().equals(c.getMakerId()) && !user.userId().equals(c.getOwner().getUserId())) {
            throw BusinessException.forbidden("Only the employee who recorded this lead can edit it.");
        }
    }

    /** Customer mobile numbers are visible to their owner/maker and to managers; masked otherwise. */
    private static boolean canSeeMobile(AuthenticatedUser user, Customer c) {
        return user.rank() > Role.EMP.rank() || user.userId().equals(c.getMakerId())
                || user.userId().equals(c.getOwner().getUserId());
    }
}

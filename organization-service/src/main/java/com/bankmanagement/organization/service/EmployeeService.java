package com.bankmanagement.organization.service;

import com.bankmanagement.common.api.PageResponse;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.DataScope;
import com.bankmanagement.common.security.Role;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.organization.client.AuthClient;
import com.bankmanagement.organization.domain.Bank;
import com.bankmanagement.organization.domain.Branch;
import com.bankmanagement.organization.domain.Employee;
import com.bankmanagement.organization.domain.RecordStatus;
import com.bankmanagement.organization.dto.EmployeeDto;
import com.bankmanagement.organization.repository.BankRepository;
import com.bankmanagement.organization.repository.CustomRoleRepository;
import com.bankmanagement.organization.repository.EmployeeRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_PAGE_SIZE = 100;

    private final EmployeeRepository employees;
    private final BankRepository banks;
    private final CustomRoleRepository customRoles;
    private final BranchService branchService;
    private final QuotaService quotaService;
    private final AuthClient authClient;
    private final AuditPublisher audit;

    @Transactional(readOnly = true)
    public PageResponse<EmployeeDto.Response> list(String role, String search, int page, int size) {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        DataScope scope = DataScope.of(actor);

        Specification<Employee> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("tenantCode"), tenant));
            if (scope.level() == DataScope.Level.BRANCH) {
                p.add(cb.equal(root.get("branchId"), scope.branchId()));
            } else if (scope.level() == DataScope.Level.SELF) {
                p.add(cb.equal(root.get("userId"), actor.userId()));
            }
            if (role != null && !role.isBlank()) {
                p.add(cb.equal(root.get("role"), role));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("username")), like), cb.like(cb.lower(root.get("designation")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<Employee> result = employees.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("name")));

        Map<UUID, Branch> branches = branchService.byId(tenant);
        Map<UUID, String> bankNames = banks.findByTenantCodeOrderByNameAsc(tenant).stream()
                .collect(Collectors.toMap(Bank::getId, Bank::getName, (a, b) -> a));
        return PageResponse.of(result, e -> toResponse(e, branches.get(e.getBranchId()), bankNames, false));
    }

    /** Own profile, available to every employee regardless of the employees permission. */
    @Transactional(readOnly = true)
    public EmployeeDto.Response me() {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        Employee e = employees.findByTenantCodeAndUserId(tenant, actor.userId())
                .orElseThrow(() -> BusinessException.notFound("Employee profile"));
        return toResponse(e, tenant, true);
    }

    @Transactional(readOnly = true)
    public EmployeeDto.Response get(UUID id) {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        Employee e = find(tenant, id);
        boolean self = Objects.equals(e.getUserId(), actor.userId());
        if (!self && !DataScope.of(actor).includes(e.getBranchId(), e.getUserId())) {
            throw BusinessException.notFound("Employee");
        }
        return toResponse(e, tenant, canSeeSalaryAccount(actor) || self);
    }

    @Transactional
    public EmployeeDto.Created create(EmployeeDto.CreateRequest req) {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();

        Branch branch = branchService.find(tenant, req.branchId());
        if (branch.getStatus() != RecordStatus.ACTIVE) {
            throw BusinessException.validation("Staff cannot be assigned to an inactive branch.", "branchId");
        }
        if (Role.BM.name().equals(actor.role()) && !branch.getId().equals(actor.branchId())) {
            throw BusinessException.forbidden("Branch Managers can only add staff to their own branch.");
        }
        assertAssignableRole(actor, tenant, req.role());
        quotaService.assertCanAddEmployee(tenant);

        String username = req.username() != null ? req.username() : usernameFromEmail(req.email());
        if (employees.existsByUsername(username)) {
            throw BusinessException.conflict("Username '" + username + "' is already taken.", "username");
        }

        Employee e = new Employee();
        e.setTenantCode(tenant);
        int number = employees.maxCodeNumber(tenant) + 1;
        e.setCodeNumber(number);
        e.setCode(CodeGenerator.employeePrefix(tenant) + number);
        e.setName(req.name().trim());
        e.setUsername(username);
        e.setEmail(req.email().toLowerCase(Locale.ROOT));
        e.setPhone(req.phone());
        e.setRole(req.role());
        e.setDesignation(req.designation().trim());
        e.setBranchId(branch.getId());
        e.setBankId(branch.getBankId());
        e.setAddress(req.address().trim());
        e.setJoinedOn(LocalDate.now(IST));
        e.setStatus(RecordStatus.ACTIVE);
        applySalaryAccount(e, req.salaryAccount());
        employees.saveAndFlush(e);

        // Credentials are created last: if this call fails the whole transaction rolls back.
        AuthClient.CreatedUser user = authClient.createUser(new AuthClient.CreateUserCommand(
                actor.isPlatformOwner() ? tenant : null, username, e.getName(), e.getEmail(), e.getRole(),
                e.getId(), e.getCode(), e.getBranchId(), null)).data();
        e.setUserId(user.userId());

        audit.record("employees", "Added employee", e.getName() + " (" + e.getCode() + ", " + e.getRole() + ")");
        return new EmployeeDto.Created(toResponse(e, tenant, false), user.username(), user.temporaryPassword());
    }

    @Transactional
    public EmployeeDto.Response update(UUID id, EmployeeDto.UpdateRequest req) {
        String tenant = SecurityUtils.currentTenant();
        Employee e = manageable(tenant, id);
        e.setName(req.name().trim());
        e.setEmail(req.email().toLowerCase(Locale.ROOT));
        e.setPhone(req.phone());
        e.setDesignation(req.designation().trim());
        e.setAddress(req.address().trim());
        if (req.salaryAccount() != null) {
            applySalaryAccount(e, req.salaryAccount());
        }
        audit.record("employees", "Updated employee", e.getName() + " (" + e.getCode() + ")");
        return toResponse(e, tenant, canSeeSalaryAccount(SecurityUtils.currentUser()));
    }

    /**
     * Branch transfer and/or promotion. The employee is signed out so their next token carries the
     * new branch and role; historic attendance/leave keeps the branch they had at the time.
     */
    @Transactional
    public EmployeeDto.Response reassign(UUID id, EmployeeDto.AssignmentRequest req) {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        Employee e = manageable(tenant, id);
        if (e.getStatus() != RecordStatus.ACTIVE) {
            throw BusinessException.rule("Only active employees can be transferred.");
        }
        Branch branch = branchService.find(tenant, req.branchId());
        if (branch.getStatus() != RecordStatus.ACTIVE) {
            throw BusinessException.validation("Staff cannot be assigned to an inactive branch.", "branchId");
        }
        if (Role.BM.name().equals(actor.role()) && !branch.getId().equals(actor.branchId())) {
            throw BusinessException.forbidden("Branch Managers cannot transfer staff to another branch.");
        }
        if (!req.role().equals(e.getRole())) {
            assertAssignableRole(actor, tenant, req.role());
        }
        String before = e.getRole() + " / " + e.getDesignation() + " @ "
                + (e.getBranchId() == null ? "no branch" : branchService.find(tenant, e.getBranchId()).getName());

        e.setBranchId(branch.getId());
        e.setBankId(branch.getBankId());
        e.setRole(req.role());
        e.setDesignation(req.designation().trim());
        if (e.getUserId() != null) {
            authClient.updateAssignment(e.getUserId(), new AuthClient.AssignmentCommand(req.role(), branch.getId()));
        }
        audit.record("employees", "Transferred / reassigned employee", e.getName() + " (" + e.getCode() + "): "
                + before + " -> " + e.getRole() + " / " + e.getDesignation() + " @ " + branch.getName()
                + (req.reason() == null ? "" : " - " + req.reason()));
        return toResponse(e, tenant, canSeeSalaryAccount(actor));
    }

    @Transactional
    public void setStatus(UUID id, RecordStatus status) {
        String tenant = SecurityUtils.currentTenant();
        Employee e = manageable(tenant, id);
        if (status == RecordStatus.ACTIVE && e.getStatus() != RecordStatus.ACTIVE) {
            quotaService.assertCanAddEmployee(tenant);
        }
        e.setStatus(status);
        if (e.getUserId() != null) {
            authClient.setStatus(e.getUserId(), new AuthClient.StatusCommand(status.name()));
        }
        audit.record("employees", status == RecordStatus.ACTIVE ? "Activated employee" : "Deactivated employee",
                e.getName() + " (" + e.getCode() + ")");
    }

    @Transactional(readOnly = true)
    public void setLocked(UUID id, boolean locked) {
        Employee e = manageable(SecurityUtils.currentTenant(), id);
        authClient.setLocked(requireUser(e), new AuthClient.LockCommand(locked));
    }

    @Transactional(readOnly = true)
    public EmployeeDto.TemporaryPassword resetPassword(UUID id) {
        Employee e = manageable(SecurityUtils.currentTenant(), id);
        AuthClient.TemporaryPassword result = authClient.resetPassword(requireUser(e)).data();
        return new EmployeeDto.TemporaryPassword(result.username(), result.temporaryPassword());
    }

    /** Same rules as auth-service: in scope, never self, strictly junior (unless platform owner). */
    private Employee manageable(String tenant, UUID id) {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        Employee e = find(tenant, id);
        if (!DataScope.of(actor).includes(e.getBranchId(), e.getUserId())) {
            throw BusinessException.notFound("Employee");
        }
        if (Objects.equals(e.getUserId(), actor.userId())) {
            throw BusinessException.forbidden("You cannot perform this action on your own record.");
        }
        if (!actor.isPlatformOwner() && Role.rankOf(e.getRole()) >= actor.rank()) {
            throw BusinessException.forbidden("You can only manage employees with a lower role than yours.");
        }
        return e;
    }

    private void assertAssignableRole(AuthenticatedUser actor, String tenant, String role) {
        if (Role.SSA.name().equals(role)) {
            throw BusinessException.forbidden("This role cannot be assigned.");
        }
        if (Role.isBuiltIn(role)) {
            if (Role.rankOf(role) >= actor.rank()) {
                throw BusinessException.forbidden("Your role (" + actor.role() + ") cannot create a '" + role + "' user.");
            }
            return;
        }
        boolean exists = customRoles.findByTenantCodeOrderByNameAsc(tenant).stream()
                .anyMatch(r -> r.getCode().equals(role) && r.getStatus() == RecordStatus.ACTIVE);
        if (!exists) {
            throw BusinessException.validation("Unknown or inactive role.", "role");
        }
        if (actor.rank() < Role.HR.rank()) {
            throw BusinessException.forbidden("Your role cannot assign custom roles.");
        }
    }

    private static void applySalaryAccount(Employee e, EmployeeDto.SalaryAccount account) {
        if (account == null) {
            return;
        }
        if (!account.accountNumber().equals(account.confirmAccountNumber())) {
            throw BusinessException.validation("Account numbers do not match.", "salaryAccount.confirmAccountNumber");
        }
        e.setSalaryBankName(account.bankName().trim());
        e.setSalaryBranchAddress(account.branchAddress().trim());
        e.setSalaryAccountNumber(account.accountNumber());
        e.setSalaryAccountLast4(account.accountNumber().substring(account.accountNumber().length() - 4));
        e.setSalaryIfsc(account.ifsc());
    }

    private static boolean canSeeSalaryAccount(AuthenticatedUser actor) {
        return actor.isPlatformOwner() || Role.SA.name().equals(actor.role()) || Role.HR.name().equals(actor.role());
    }

    private static String usernameFromEmail(String email) {
        String local = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        if (local.length() < 3) {
            local = local + "user";
        }
        return local.length() > 32 ? local.substring(0, 32) : local;
    }

    private static UUID requireUser(Employee e) {
        if (e.getUserId() == null) {
            throw BusinessException.rule("This employee has no login account.");
        }
        return e.getUserId();
    }

    private Employee find(String tenant, UUID id) {
        return employees.findByIdAndTenantCode(id, tenant).orElseThrow(() -> BusinessException.notFound("Employee"));
    }

    private EmployeeDto.Response toResponse(Employee e, String tenant, boolean reveal) {
        Branch branch = e.getBranchId() == null ? null : branchService.find(tenant, e.getBranchId());
        String bankName = e.getBankId() == null ? null
                : banks.findByIdAndTenantCode(e.getBankId(), tenant).map(Bank::getName).orElse(null);
        return EmployeeDto.Response.from(e, bankName, branch == null ? null : branch.getName(),
                branch != null && branch.isHeadquarters(), reveal);
    }

    private static EmployeeDto.Response toResponse(Employee e, Branch branch, Map<UUID, String> bankNames, boolean reveal) {
        return EmployeeDto.Response.from(e, bankNames.get(e.getBankId()), branch == null ? null : branch.getName(),
                branch != null && branch.isHeadquarters(), reveal);
    }
}

package com.bankmanagement.organization.service;

import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.DataScope;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.organization.domain.Branch;
import com.bankmanagement.organization.domain.RecordStatus;
import com.bankmanagement.organization.dto.AnnouncementDto;
import com.bankmanagement.organization.repository.BranchRepository;
import com.bankmanagement.organization.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Organization part of the dashboard; attendance and incentive services expose their own parts. */
@Service
@RequiredArgsConstructor
public class OrganizationDashboardService {

    private static final int ANNOUNCEMENTS_ON_DASHBOARD = 3;

    private final BranchRepository branches;
    private final EmployeeRepository employees;
    private final AnnouncementService announcementService;

    /** {@code activeStaffInScope} is null for employees, who only see their own data. */
    public record Summary(long activeBranches, Long activeStaffInScope, UUID myBranchId, String myBranchName,
                          Integer myBranchRadiusMeters, List<AnnouncementDto.Response> announcements) {
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        DataScope scope = DataScope.of(user);

        Long staff = switch (scope.level()) {
            case TENANT -> employees.countByTenantCodeAndStatus(tenant, RecordStatus.ACTIVE);
            case BRANCH -> employees.countByTenantCodeAndBranchIdAndStatus(tenant, scope.branchId(), RecordStatus.ACTIVE);
            case SELF -> null;
        };
        Branch myBranch = user.branchId() == null ? null
                : branches.findByIdAndTenantCode(user.branchId(), tenant).orElse(null);
        List<AnnouncementDto.Response> notices = user.hasPermission("announcements", 'R')
                ? announcementService.list().stream().limit(ANNOUNCEMENTS_ON_DASHBOARD).toList()
                : List.of();
        return new Summary(branches.countByTenantCodeAndStatus(tenant, RecordStatus.ACTIVE), staff,
                myBranch == null ? null : myBranch.getId(), myBranch == null ? null : myBranch.getName(),
                myBranch == null ? null : myBranch.getRadiusMeters(), notices);
    }
}

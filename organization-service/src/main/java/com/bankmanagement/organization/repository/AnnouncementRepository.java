package com.bankmanagement.organization.repository;

import com.bankmanagement.organization.domain.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID>, JpaSpecificationExecutor<Announcement> {

    Optional<Announcement> findByIdAndTenantCode(UUID id, String tenantCode);

    List<Announcement> findByTenantCodeAndBranchId(String tenantCode, UUID branchId);
}

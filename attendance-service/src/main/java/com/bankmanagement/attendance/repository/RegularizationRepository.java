package com.bankmanagement.attendance.repository;

import com.bankmanagement.attendance.domain.Enums;
import com.bankmanagement.attendance.domain.Regularization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface RegularizationRepository extends JpaRepository<Regularization, UUID>,
        JpaSpecificationExecutor<Regularization> {

    Optional<Regularization> findByIdAndTenantCode(UUID id, String tenantCode);

    boolean existsByTenantCodeAndSubjectUserIdAndWorkDateAndStatus(String tenantCode, UUID userId, LocalDate date,
                                                                    Enums.RequestStatus status);
}

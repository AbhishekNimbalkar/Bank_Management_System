package com.bankmanagement.attendance.repository;

import com.bankmanagement.attendance.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    List<Holiday> findByTenantCodeAndHolidayDateBetweenOrderByHolidayDateAsc(String tenantCode, LocalDate from,
                                                                             LocalDate to);

    Optional<Holiday> findByIdAndTenantCode(UUID id, String tenantCode);

    boolean existsByTenantCodeAndHolidayDateAndNameIgnoreCase(String tenantCode, LocalDate date, String name);
}

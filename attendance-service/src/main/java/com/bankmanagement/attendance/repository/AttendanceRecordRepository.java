package com.bankmanagement.attendance.repository;

import com.bankmanagement.attendance.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID>,
        JpaSpecificationExecutor<AttendanceRecord> {

    Optional<AttendanceRecord> findByTenantCodeAndSubjectUserIdAndWorkDate(String tenantCode, UUID userId, LocalDate date);
}

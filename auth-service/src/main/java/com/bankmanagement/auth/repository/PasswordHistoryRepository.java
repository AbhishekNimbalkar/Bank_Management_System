package com.bankmanagement.auth.repository;

import com.bankmanagement.auth.domain.PasswordHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistoryEntry, UUID> {

    List<PasswordHistoryEntry> findByUserIdOrderByCreatedAtDesc(UUID userId);
}

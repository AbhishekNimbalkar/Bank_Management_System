package com.bankmanagement.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Logo image of a bank, kept apart from {@link Bank} so listing banks never loads image bytes. One row
 * per bank; replacing the logo overwrites it.
 */
@Getter
@Setter
@Entity
@Table(name = "bank_logos")
public class BankLogo {

    @Id
    @Column(name = "bank_id")
    private UUID bankId;

    @Column(name = "tenant_code", nullable = false, length = 40, updatable = false)
    private String tenantCode;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "data", nullable = false)
    private byte[] data;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;
}

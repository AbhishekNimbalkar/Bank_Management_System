package com.bankmanagement.incentive.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Customer deal (loan file or deposit account). Created as a LEAD; only a senior other than the
 * creator can mark it CONVERTED, which is what counts towards incentives (maker-checker).
 */
@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "customers")
public class Customer extends TenantScopedEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 10)
    private String mobile;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false, length = 60)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 10)
    private Enums.ProductType productType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Embedded
    private Owner owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Enums.CustomerStatus status = Enums.CustomerStatus.LEAD;

    @Column(name = "lead_date", nullable = false)
    private LocalDate leadDate;

    @Column(name = "converted_date")
    private LocalDate convertedDate;

    @Column(length = 300)
    private String notes;

    @Column(name = "maker_id", nullable = false)
    private UUID makerId;

    @Column(name = "checker_id")
    private UUID checkerId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 200)
    private String decisionNote;
}

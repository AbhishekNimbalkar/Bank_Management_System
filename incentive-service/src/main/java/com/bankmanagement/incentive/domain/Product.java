package com.bankmanagement.incentive.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/** Loan or deposit product offered by the organization. */
@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "products", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "name"}))
@AttributeOverride(name = "id", column = @Column(name = "product_id"))
public class Product extends TenantScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.ProductType type;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercent;

    @Column(length = 60)
    private String tenure;

    @Column(nullable = false)
    private boolean active = true;
}

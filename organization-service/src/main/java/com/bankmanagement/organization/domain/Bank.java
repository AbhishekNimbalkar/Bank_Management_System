package com.bankmanagement.organization.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "banks")
@AttributeOverride(name = "id", column = @Column(name = "bank_id"))
public class Bank extends TenantScopedEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 6)
    private String code;

    @Embedded
    private Location location = new Location();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;
}

package com.bankmanagement.organization.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
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
@Table(name = "designations")
@AttributeOverride(name = "id", column = @Column(name = "designation_id"))
public class Designation extends TenantScopedEntity {

    @Column(nullable = false, length = 60)
    private String title;

    @Column(length = 60)
    private String department;

    @Column(length = 40)
    private String grade;

    @Column(length = 240)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;
}

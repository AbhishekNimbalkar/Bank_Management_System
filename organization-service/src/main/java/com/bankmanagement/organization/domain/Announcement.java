package com.bankmanagement.organization.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.UUID;

/** Notice shown to everyone in the organization, or only to one branch when {@code branchId} is set. */
@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "announcements")
public class Announcement extends TenantScopedEntity {

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "author_name", nullable = false, length = 80)
    private String authorName;

    @Column(name = "published_on", nullable = false)
    private LocalDate publishedOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;
}

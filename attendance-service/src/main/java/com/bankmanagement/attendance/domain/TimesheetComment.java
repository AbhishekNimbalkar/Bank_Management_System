package com.bankmanagement.attendance.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

/** A manager's note on an employee's timesheet entry, visible to that employee and to their managers. */
@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "timesheet_comments")
@AttributeOverride(name = "id", column = @Column(name = "timesheet_comment_id"))
public class TimesheetComment extends TenantScopedEntity {

    @Column(name = "timesheet_id", nullable = false, updatable = false)
    private UUID timesheetId;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private UUID authorUserId;

    @Column(name = "author_name", nullable = false, length = 80)
    private String authorName;

    @Column(name = "author_role", nullable = false, length = 10)
    private String authorRole;

    @Column(name = "body", nullable = false, length = 500)
    private String body;
}

-- Manual attendance (marked by a manager with the location worked at), HR / Super Admin edits, and
-- manager comments on timesheet entries.

ALTER TABLE attendance_records
    ADD COLUMN source         VARCHAR(12) NOT NULL DEFAULT 'GPS',
    ADD COLUMN latitude       DOUBLE PRECISION,
    ADD COLUMN longitude      DOUBLE PRECISION,
    ADD COLUMN marked_by      UUID,
    ADD COLUMN marked_by_name VARCHAR(80),
    ADD COLUMN edited_by      UUID,
    ADD COLUMN edited_by_name VARCHAR(80),
    ADD COLUMN edited_at      TIMESTAMP WITH TIME ZONE,
    ADD COLUMN edit_reason    VARCHAR(200),
    ADD CONSTRAINT ck_attendance_source CHECK (source IN ('GPS', 'MANUAL', 'CORRECTION'));

-- Records written by an approved correction request before this column existed.
UPDATE attendance_records SET source = 'CORRECTION' WHERE note = 'Corrected via approved request';

CREATE TABLE timesheet_comments (
    timesheet_comment_id UUID PRIMARY KEY,
    tenant_code          VARCHAR(40)  NOT NULL,
    timesheet_id         UUID         NOT NULL REFERENCES timesheets (timesheet_id),
    author_user_id       UUID         NOT NULL,
    author_name          VARCHAR(80)  NOT NULL,
    author_role          VARCHAR(10)  NOT NULL,
    body                 VARCHAR(500) NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by           VARCHAR(64),
    updated_by           VARCHAR(64),
    deleted_at           TIMESTAMP WITH TIME ZONE,
    deleted_by           VARCHAR(64),
    version              BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_timesheet_comments_entry ON timesheet_comments (tenant_code, timesheet_id);

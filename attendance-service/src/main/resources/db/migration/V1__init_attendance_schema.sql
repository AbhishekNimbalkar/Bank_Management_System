-- Attendance service schema: daily attendance, shift rules, corrections, leave, holidays, timesheets.

CREATE TABLE attendance_records (
    id             UUID PRIMARY KEY,
    tenant_code    VARCHAR(40) NOT NULL,
    user_id        UUID        NOT NULL,
    employee_name  VARCHAR(80) NOT NULL,
    employee_code  VARCHAR(20),
    employee_role  VARCHAR(10) NOT NULL,
    branch_id      UUID,
    work_date      DATE        NOT NULL,
    check_in       TIME,
    check_out      TIME,
    status         VARCHAR(20) NOT NULL,
    distance_meters INTEGER,
    hours_worked   NUMERIC(4, 2),
    note           VARCHAR(200),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by     VARCHAR(64),
    updated_by     VARCHAR(64),
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_attendance_user_day UNIQUE (tenant_code, user_id, work_date)
);
CREATE INDEX ix_attendance_branch_day ON attendance_records (tenant_code, branch_id, work_date);
CREATE INDEX ix_attendance_day ON attendance_records (tenant_code, work_date);

CREATE TABLE attendance_rules (
    id                      UUID PRIMARY KEY,
    tenant_code             VARCHAR(40)   NOT NULL,
    shift_start             TIME          NOT NULL,
    grace_minutes           INTEGER       NOT NULL,
    half_day_hours          NUMERIC(4, 2) NOT NULL,
    max_gps_accuracy_meters INTEGER       NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by              VARCHAR(64),
    updated_by              VARCHAR(64),
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_attendance_rules_tenant UNIQUE (tenant_code)
);

CREATE TABLE regularizations (
    id            UUID PRIMARY KEY,
    tenant_code   VARCHAR(40)  NOT NULL,
    user_id       UUID         NOT NULL,
    employee_name VARCHAR(80)  NOT NULL,
    employee_code VARCHAR(20),
    employee_role VARCHAR(10)  NOT NULL,
    branch_id     UUID,
    work_date     DATE         NOT NULL,
    check_in      TIME         NOT NULL,
    check_out     TIME         NOT NULL,
    reason        VARCHAR(300) NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    decided_by    UUID,
    decided_at    TIMESTAMP WITH TIME ZONE,
    decision_note VARCHAR(200),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(64),
    updated_by    VARCHAR(64),
    version       BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_regularizations_scope ON regularizations (tenant_code, branch_id, status);
CREATE INDEX ix_regularizations_user ON regularizations (tenant_code, user_id, work_date);

CREATE TABLE leave_policies (
    id               UUID PRIMARY KEY,
    tenant_code      VARCHAR(40)   NOT NULL,
    name             VARCHAR(60)   NOT NULL,
    code             VARCHAR(8)    NOT NULL,
    annual_days      NUMERIC(5, 1) NOT NULL,
    carry_forward    BOOLEAN       NOT NULL,
    paid             BOOLEAN       NOT NULL,
    half_day_allowed BOOLEAN       NOT NULL,
    active           BOOLEAN       NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by       VARCHAR(64),
    updated_by       VARCHAR(64),
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_leave_policies_tenant_code UNIQUE (tenant_code, code)
);

CREATE TABLE leave_requests (
    id            UUID PRIMARY KEY,
    tenant_code   VARCHAR(40)   NOT NULL,
    user_id       UUID          NOT NULL,
    employee_name VARCHAR(80)   NOT NULL,
    employee_code VARCHAR(20),
    employee_role VARCHAR(10)   NOT NULL,
    branch_id     UUID,
    policy_id     UUID          NOT NULL REFERENCES leave_policies (id),
    day_type      VARCHAR(16)   NOT NULL,
    from_date     DATE          NOT NULL,
    to_date       DATE          NOT NULL,
    days          NUMERIC(5, 1) NOT NULL,
    reason        VARCHAR(300)  NOT NULL,
    status        VARCHAR(16)   NOT NULL,
    decided_by    UUID,
    decided_at    TIMESTAMP WITH TIME ZONE,
    decision_note VARCHAR(200),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(64),
    updated_by    VARCHAR(64),
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_leave_dates CHECK (to_date >= from_date)
);
CREATE INDEX ix_leave_requests_user ON leave_requests (tenant_code, user_id, from_date);
CREATE INDEX ix_leave_requests_scope ON leave_requests (tenant_code, branch_id, status);

CREATE TABLE holidays (
    id           UUID PRIMARY KEY,
    tenant_code  VARCHAR(40) NOT NULL,
    holiday_date DATE        NOT NULL,
    name         VARCHAR(80) NOT NULL,
    type         VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by   VARCHAR(64),
    updated_by   VARCHAR(64),
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_holidays UNIQUE (tenant_code, holiday_date, name)
);

CREATE TABLE timesheets (
    id            UUID PRIMARY KEY,
    tenant_code   VARCHAR(40)   NOT NULL,
    user_id       UUID          NOT NULL,
    employee_name VARCHAR(80)   NOT NULL,
    employee_code VARCHAR(20),
    employee_role VARCHAR(10)   NOT NULL,
    branch_id     UUID,
    work_date     DATE          NOT NULL,
    task          VARCHAR(200)  NOT NULL,
    hours         NUMERIC(4, 1) NOT NULL,
    notes         VARCHAR(300),
    status        VARCHAR(16)   NOT NULL,
    decided_by    UUID,
    decided_at    TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(64),
    updated_by    VARCHAR(64),
    version       BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_timesheets_user ON timesheets (tenant_code, user_id, work_date);

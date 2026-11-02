-- Organization service schema: tenants (platform), banks, branches, employees, designations, custom roles.

CREATE TABLE tenants (
    id                   UUID PRIMARY KEY,
    code                 VARCHAR(20)  NOT NULL,
    organization_name    VARCHAR(100) NOT NULL,
    headquarters         VARCHAR(80),
    plan                 VARCHAR(16)  NOT NULL,
    status               VARCHAR(16)  NOT NULL,
    max_banks            INTEGER      NOT NULL,
    max_branches         INTEGER      NOT NULL,
    max_employees        INTEGER      NOT NULL,
    max_customers        INTEGER      NOT NULL,
    feature_geofencing   BOOLEAN      NOT NULL,
    feature_incentives   BOOLEAN      NOT NULL,
    feature_multi_branch BOOLEAN      NOT NULL,
    feature_audit_export BOOLEAN      NOT NULL,
    admin_user_id        UUID,
    admin_name           VARCHAR(80)  NOT NULL,
    admin_username       VARCHAR(32)  NOT NULL,
    admin_email          VARCHAR(120),
    admin_phone          VARCHAR(10),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by           VARCHAR(64),
    updated_by           VARCHAR(64),
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_tenants_code UNIQUE (code),
    CONSTRAINT ck_tenants_plan CHECK (plan IN ('STARTER', 'GROWTH', 'PRO', 'ENTERPRISE')),
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE banks (
    id          UUID PRIMARY KEY,
    tenant_code VARCHAR(40) NOT NULL,
    name        VARCHAR(80) NOT NULL,
    code        VARCHAR(6)  NOT NULL,
    state       VARCHAR(40),
    city        VARCHAR(60),
    pincode     VARCHAR(6),
    street      VARCHAR(120),
    address     VARCHAR(240),
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    status      VARCHAR(16) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by  VARCHAR(64),
    updated_by  VARCHAR(64),
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_banks_tenant_code UNIQUE (tenant_code, code)
);

CREATE TABLE branches (
    id            UUID PRIMARY KEY,
    tenant_code   VARCHAR(40) NOT NULL,
    bank_id       UUID        NOT NULL REFERENCES banks (id),
    name          VARCHAR(80) NOT NULL,
    ifsc          VARCHAR(11) NOT NULL,
    state         VARCHAR(40),
    city          VARCHAR(60),
    pincode       VARCHAR(6),
    street        VARCHAR(120),
    address       VARCHAR(240),
    latitude      DOUBLE PRECISION NOT NULL,
    longitude     DOUBLE PRECISION NOT NULL,
    radius_meters INTEGER     NOT NULL,
    headquarters  BOOLEAN     NOT NULL DEFAULT FALSE,
    status        VARCHAR(16) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(64),
    updated_by    VARCHAR(64),
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_branches_tenant_ifsc UNIQUE (tenant_code, ifsc),
    CONSTRAINT ck_branches_radius CHECK (radius_meters BETWEEN 50 AND 1000)
);
CREATE INDEX ix_branches_bank ON branches (tenant_code, bank_id);

CREATE TABLE employees (
    id                    UUID PRIMARY KEY,
    tenant_code           VARCHAR(40)  NOT NULL,
    user_id               UUID,
    code                  VARCHAR(20)  NOT NULL,
    code_number           INTEGER      NOT NULL,
    name                  VARCHAR(80)  NOT NULL,
    username              VARCHAR(32)  NOT NULL,
    email                 VARCHAR(120) NOT NULL,
    phone                 VARCHAR(10)  NOT NULL,
    role                  VARCHAR(10)  NOT NULL,
    designation           VARCHAR(60)  NOT NULL,
    bank_id               UUID         NOT NULL REFERENCES banks (id),
    branch_id             UUID         NOT NULL REFERENCES branches (id),
    address               VARCHAR(240),
    joined_on             DATE         NOT NULL,
    status                VARCHAR(16)  NOT NULL,
    salary_bank_name      VARCHAR(60),
    salary_branch_address VARCHAR(120),
    salary_account_number VARCHAR(120),
    salary_account_last4  VARCHAR(4),
    salary_ifsc           VARCHAR(11),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by            VARCHAR(64),
    updated_by            VARCHAR(64),
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_employees_tenant_code UNIQUE (tenant_code, code),
    CONSTRAINT uk_employees_tenant_username UNIQUE (tenant_code, username)
);
CREATE INDEX ix_employees_branch ON employees (tenant_code, branch_id);
CREATE INDEX ix_employees_user ON employees (tenant_code, user_id);

CREATE TABLE designations (
    id          UUID PRIMARY KEY,
    tenant_code VARCHAR(40) NOT NULL,
    title       VARCHAR(60) NOT NULL,
    department  VARCHAR(60),
    grade       VARCHAR(40),
    description VARCHAR(240),
    status      VARCHAR(16) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by  VARCHAR(64),
    updated_by  VARCHAR(64),
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_designations_tenant_title UNIQUE (tenant_code, title)
);

CREATE TABLE custom_roles (
    id          UUID PRIMARY KEY,
    tenant_code VARCHAR(40) NOT NULL,
    code        VARCHAR(10) NOT NULL,
    name        VARCHAR(60) NOT NULL,
    category    VARCHAR(60),
    description VARCHAR(240),
    status      VARCHAR(16) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by  VARCHAR(64),
    updated_by  VARCHAR(64),
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_custom_roles_tenant_code UNIQUE (tenant_code, code)
);

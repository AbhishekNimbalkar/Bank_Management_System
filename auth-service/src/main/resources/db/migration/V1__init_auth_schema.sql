-- Auth service schema: login identities, refresh tokens, password history, permission overrides.

CREATE TABLE users (
    id                   UUID PRIMARY KEY,
    tenant_code          VARCHAR(40)  NOT NULL,
    username             VARCHAR(32)  NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    display_name         VARCHAR(80)  NOT NULL,
    email                VARCHAR(120),
    role                 VARCHAR(10)  NOT NULL,
    employee_id          UUID,
    employee_code        VARCHAR(20),
    branch_id            UUID,
    status               VARCHAR(16)  NOT NULL,
    admin_locked         BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_attempts      INTEGER      NOT NULL DEFAULT 0,
    lockout_count        INTEGER      NOT NULL DEFAULT 0,
    locked_until         TIMESTAMP WITH TIME ZONE,
    must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
    password_changed_at  TIMESTAMP WITH TIME ZONE,
    last_login_at        TIMESTAMP WITH TIME ZONE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by           VARCHAR(64),
    updated_by           VARCHAR(64),
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_tenant_username UNIQUE (tenant_code, username),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_users_role ON users (role);
CREATE INDEX ix_users_employee ON users (tenant_code, employee_id);

CREATE TABLE password_history (
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_password_history_user ON password_history (user_id, created_at DESC);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tenant_code VARCHAR(40) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    family_id   UUID        NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(200),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expiry ON refresh_tokens (expires_at);

CREATE TABLE role_permissions (
    id          UUID PRIMARY KEY,
    tenant_code VARCHAR(40) NOT NULL,
    role_code   VARCHAR(10) NOT NULL,
    module      VARCHAR(40) NOT NULL,
    actions     VARCHAR(4)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by  VARCHAR(64),
    updated_by  VARCHAR(64),
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_role_permissions UNIQUE (tenant_code, role_code, module)
);

CREATE TABLE tenant_access (
    tenant_code VARCHAR(40) PRIMARY KEY,
    status      VARCHAR(16) NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_tenant_access_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

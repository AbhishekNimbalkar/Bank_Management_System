CREATE TABLE announcements (
    id           UUID PRIMARY KEY,
    tenant_code  VARCHAR(40)   NOT NULL,
    title        VARCHAR(120)  NOT NULL,
    body         VARCHAR(2000) NOT NULL,
    pinned       BOOLEAN       NOT NULL DEFAULT FALSE,
    branch_id    UUID REFERENCES branches (id) ON DELETE CASCADE,
    author_id    UUID          NOT NULL,
    author_name  VARCHAR(80)   NOT NULL,
    published_on DATE          NOT NULL,
    expires_on   DATE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by   VARCHAR(64),
    updated_by   VARCHAR(64),
    version      BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_announcements_tenant ON announcements (tenant_code, published_on DESC);

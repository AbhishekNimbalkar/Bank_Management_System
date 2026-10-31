CREATE TABLE bank_logos (
    bank_id      UUID PRIMARY KEY REFERENCES banks (bank_id) ON DELETE CASCADE,
    tenant_code  VARCHAR(40)  NOT NULL,
    content_type VARCHAR(40)  NOT NULL,
    size_bytes   INTEGER      NOT NULL,
    data         BYTEA        NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by   VARCHAR(64)
);

CREATE INDEX ix_bank_logos_tenant ON bank_logos (tenant_code);


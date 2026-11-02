-- Audit service schema: append-only, hash-chained audit log per tenant.

CREATE TABLE audit_chain_heads (
    tenant_code   VARCHAR(40) PRIMARY KEY,
    last_sequence BIGINT      NOT NULL,
    last_hash     VARCHAR(64) NOT NULL
);

CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY,
    tenant_code     VARCHAR(40) NOT NULL,
    sequence_no     BIGINT      NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_id        VARCHAR(64),
    actor_name      VARCHAR(80),
    actor_role      VARCHAR(10),
    impersonator_id VARCHAR(64),
    module          VARCHAR(40) NOT NULL,
    action          VARCHAR(80) NOT NULL,
    detail          VARCHAR(500),
    ip_address      VARCHAR(45),
    request_id      VARCHAR(64),
    source_service  VARCHAR(40),
    prev_hash       VARCHAR(64) NOT NULL,
    hash            VARCHAR(64) NOT NULL,
    CONSTRAINT uk_audit_logs_tenant_seq UNIQUE (tenant_code, sequence_no)
);
CREATE INDEX ix_audit_logs_tenant_time ON audit_logs (tenant_code, occurred_at DESC);
CREATE INDEX ix_audit_logs_tenant_module ON audit_logs (tenant_code, module);

-- Defence in depth: the database itself refuses to modify or delete audit entries.
CREATE OR REPLACE FUNCTION audit_logs_block_changes() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_no_update
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_block_changes();

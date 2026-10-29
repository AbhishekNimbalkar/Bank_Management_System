-- Organizations can be archived (terminal state; data kept for audit).
ALTER TABLE tenants DROP CONSTRAINT ck_tenants_status;
ALTER TABLE tenants ADD CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'));
ALTER TABLE tenants ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;

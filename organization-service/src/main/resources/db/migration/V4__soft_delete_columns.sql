-- Who/when a row was deleted. Rows are soft-deleted (kept, hidden by the application).

ALTER TABLE tenants       ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE banks         ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE branches      ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE employees     ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE designations  ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE custom_roles  ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE announcements ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);

-- Uniqueness applies to live rows only, so a deleted code/name/IFSC can be used again.
ALTER TABLE banks DROP CONSTRAINT uk_banks_tenant_code;
CREATE UNIQUE INDEX uk_banks_tenant_code ON banks (tenant_code, code) WHERE deleted_at IS NULL;

ALTER TABLE branches DROP CONSTRAINT uk_branches_tenant_ifsc;
CREATE UNIQUE INDEX uk_branches_tenant_ifsc ON branches (tenant_code, ifsc) WHERE deleted_at IS NULL;

ALTER TABLE designations DROP CONSTRAINT uk_designations_tenant_title;
CREATE UNIQUE INDEX uk_designations_tenant_title ON designations (tenant_code, title) WHERE deleted_at IS NULL;

ALTER TABLE custom_roles DROP CONSTRAINT uk_custom_roles_tenant_code;
CREATE UNIQUE INDEX uk_custom_roles_tenant_code ON custom_roles (tenant_code, code) WHERE deleted_at IS NULL;

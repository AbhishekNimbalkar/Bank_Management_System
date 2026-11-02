-- Who/when a row was deleted. Rows are soft-deleted (kept, hidden by the application).

ALTER TABLE users            ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE role_permissions ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);

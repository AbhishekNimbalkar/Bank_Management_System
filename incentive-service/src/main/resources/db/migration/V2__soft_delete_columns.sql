-- Who/when a row was deleted. Rows are soft-deleted (kept, hidden by the application).

ALTER TABLE products        ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE incentive_plans ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE customers       ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);

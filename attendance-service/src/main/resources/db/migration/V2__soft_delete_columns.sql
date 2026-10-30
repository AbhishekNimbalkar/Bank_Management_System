-- Who/when a row was deleted. Rows are soft-deleted (kept, hidden by the application).

ALTER TABLE attendance_records ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE attendance_rules   ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE regularizations    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE leave_policies     ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE leave_requests     ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE holidays           ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);
ALTER TABLE timesheets         ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE, ADD COLUMN deleted_by VARCHAR(64);

-- A deleted holiday must not block adding the same one again.
ALTER TABLE holidays DROP CONSTRAINT uk_holidays;
CREATE UNIQUE INDEX uk_holidays ON holidays (tenant_code, holiday_date, name) WHERE deleted_at IS NULL;

-- A client is registered without any bank or branch; it adds its own afterwards. Its Super Admin is
-- created at registration, so an employee's bank and branch are optional (organization-wide admin).

ALTER TABLE employees ALTER COLUMN bank_id   DROP NOT NULL;
ALTER TABLE employees ALTER COLUMN branch_id DROP NOT NULL;

-- A bank is registered without branches; its branches are added afterwards and the first one becomes
-- the headquarters. At most one live headquarters branch per bank.

CREATE UNIQUE INDEX uk_branches_bank_headquarters ON branches (tenant_code, bank_id)
    WHERE headquarters AND deleted_at IS NULL;

-- Incentive service schema: products, monthly incentive plans, customer leads/conversions.

CREATE TABLE products (
    id           UUID PRIMARY KEY,
    tenant_code  VARCHAR(40)   NOT NULL,
    type         VARCHAR(10)   NOT NULL,
    name         VARCHAR(60)   NOT NULL,
    rate_percent NUMERIC(5, 2) NOT NULL,
    tenure       VARCHAR(60),
    active       BOOLEAN       NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by   VARCHAR(64),
    updated_by   VARCHAR(64),
    version      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_products_tenant_name UNIQUE (tenant_code, name),
    CONSTRAINT ck_products_type CHECK (type IN ('LOAN', 'DEPOSIT'))
);

CREATE TABLE incentive_plans (
    id                 UUID PRIMARY KEY,
    tenant_code        VARCHAR(40)    NOT NULL,
    name               VARCHAR(60)    NOT NULL,
    category           VARCHAR(10)    NOT NULL,
    plan_month         VARCHAR(7)     NOT NULL,
    target_amount      NUMERIC(15, 2) NOT NULL,
    threshold_percent  INTEGER        NOT NULL,
    base_rate_percent  NUMERIC(5, 3)  NOT NULL,
    bonus_rate_percent NUMERIC(5, 3)  NOT NULL,
    status             VARCHAR(10)    NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by         VARCHAR(64),
    updated_by         VARCHAR(64),
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_incentive_plans UNIQUE (tenant_code, plan_month, name),
    CONSTRAINT ck_incentive_plans_threshold CHECK (threshold_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_incentive_plans_target CHECK (target_amount > 0)
);

CREATE TABLE customers (
    id                UUID PRIMARY KEY,
    tenant_code       VARCHAR(40)    NOT NULL,
    name              VARCHAR(80)    NOT NULL,
    mobile            VARCHAR(10)    NOT NULL,
    product_id        UUID           NOT NULL REFERENCES products (id),
    product_name      VARCHAR(60)    NOT NULL,
    product_type      VARCHAR(10)    NOT NULL,
    amount            NUMERIC(15, 2) NOT NULL,
    owner_user_id     UUID           NOT NULL,
    owner_name        VARCHAR(80)    NOT NULL,
    owner_code        VARCHAR(20),
    owner_role        VARCHAR(10)    NOT NULL,
    owner_designation VARCHAR(60),
    owner_branch_id   UUID,
    status            VARCHAR(12)    NOT NULL,
    lead_date         DATE           NOT NULL,
    converted_date    DATE,
    notes             VARCHAR(300),
    maker_id          UUID           NOT NULL,
    checker_id        UUID,
    decided_at        TIMESTAMP WITH TIME ZONE,
    decision_note     VARCHAR(200),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by        VARCHAR(64),
    updated_by        VARCHAR(64),
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_customers_amount CHECK (amount > 0),
    CONSTRAINT ck_customers_maker_checker CHECK (checker_id IS NULL OR checker_id <> maker_id)
);
CREATE INDEX ix_customers_conversions ON customers (tenant_code, status, converted_date);
CREATE INDEX ix_customers_owner ON customers (tenant_code, owner_user_id);
CREATE INDEX ix_customers_branch ON customers (tenant_code, owner_branch_id);
CREATE INDEX ix_customers_mobile ON customers (tenant_code, mobile, product_id);

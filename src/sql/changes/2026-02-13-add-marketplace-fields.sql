ALTER TABLE organizations
ADD COLUMN marketplace_account_id TEXT UNIQUE,
ADD COLUMN marketplace_order_id TEXT,
ADD COLUMN marketplace_status TEXT DEFAULT 'none'
    CHECK (marketplace_status IN ('none', 'pending', 'active', 'suspended', 'cancelled')),
ADD COLUMN usage_reporting_id TEXT,
ADD COLUMN gcp_project_id TEXT;

ALTER TABLE users
ADD COLUMN procurement_account_id TEXT,
ADD COLUMN google_user_identity TEXT;

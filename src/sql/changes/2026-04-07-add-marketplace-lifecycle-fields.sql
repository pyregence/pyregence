-- Forward migration for databases on the 2026-02-13 shipped form.
-- Brings them up to the Phase 2 marketplace lifecycle schema.

-- marketplace_status: text+CHECK -> enum (adds pending_cancellation value)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'marketplace_status') THEN
    CREATE TYPE marketplace_status AS ENUM (
      'none', 'pending', 'active', 'pending_cancellation', 'suspended', 'cancelled'
    );
  END IF;
END$$;

ALTER TABLE organizations
  DROP CONSTRAINT IF EXISTS organizations_marketplace_status_check;

DO $$
BEGIN
  IF (SELECT data_type
        FROM information_schema.columns
        WHERE table_name = 'organizations'
          AND column_name = 'marketplace_status') = 'text' THEN
    ALTER TABLE organizations ALTER COLUMN marketplace_status DROP DEFAULT;
    ALTER TABLE organizations
      ALTER COLUMN marketplace_status TYPE marketplace_status
      USING marketplace_status::text::marketplace_status;
    ALTER TABLE organizations
      ALTER COLUMN marketplace_status SET DEFAULT 'none'::marketplace_status;
    ALTER TABLE organizations
      ALTER COLUMN marketplace_status SET NOT NULL;
  END IF;
END$$;

-- pre_suspend_tier: remembers tier at suspension so reactivation can restore it
ALTER TABLE organizations
  ADD COLUMN IF NOT EXISTS pre_suspend_tier subscription_tier;

-- One GAIA identity per user (partial index allows multiple NULL non-marketplace users)
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_google_user_identity
    ON users(google_user_identity)
    WHERE google_user_identity IS NOT NULL;

-- Backfill any existing procurement_account_id values to the canonical 'accounts/X' form
UPDATE users
   SET procurement_account_id = 'accounts/' || procurement_account_id
 WHERE procurement_account_id IS NOT NULL
   AND procurement_account_id NOT LIKE 'accounts/%';

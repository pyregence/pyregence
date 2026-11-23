BEGIN;

-- 1. Migrate existing values.
-- TODO check prod to see if these 4 UPDATEs are necessary
UPDATE organizations
SET subscription_tier = 'tier1_free_registered'
WHERE subscription_tier = 'community';

UPDATE organizations
SET subscription_tier = 'tier3_enterprise'
WHERE subscription_tier = 'enterprise';

-- TODO this UPDATEs needs to be based on customer size, i assume we don't have any customer-sizes so
-- any previous tier2_pros become a small utility.
UPDATE organizations
SET subscription_tier = 'tier2_pro'
WHERE subscription_tier = 'small_utility';

-- 2. Remove the column's default.
ALTER TABLE organizations
ALTER COLUMN subscription_tier DROP DEFAULT;

-- 3. Rename the existing ENUM.
ALTER TYPE subscription_tier
RENAME TO subscription_tier_old;

-- 4. Create the replacement ENUM without tier1_basic_paid.
CREATE TYPE subscription_tier AS ENUM (
  'community',
  'small_utility',
  'mid_range_utility',
  'enterprise'
);

-- 5. Convert the existing column to the replacement ENUM.
ALTER TABLE organizations
ALTER COLUMN subscription_tier
TYPE subscription_tier
USING subscription_tier::text::subscription_tier;

-- 6. Restore the default.
ALTER TABLE organizations
ALTER COLUMN subscription_tier
SET DEFAULT 'community';

-- 7. Remove the original ENUM.
DROP TYPE subscription_tier_old;

COMMIT;

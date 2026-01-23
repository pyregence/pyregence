CREATE TYPE subscription_tier AS ENUM (
  'tier1_free_registered',
  'tier1_basic_paid',
  'tier2_pro',
  'tier3_enterprise'
);

ALTER TABLE organizations
ADD COLUMN subscription_tier subscription_tier NOT NULL DEFAULT 'tier1_free_registered',
ADD COLUMN max_seats integer NOT NULL DEFAULT 1;

ALTER TABLE organizations
ADD CONSTRAINT org_tier_seat_rules CHECK (
  (subscription_tier IN ('tier1_free_registered', 'tier1_basic_paid') AND max_seats = 1)
  OR
  (subscription_tier IN ('tier2_pro', 'tier3_enterprise') AND max_seats >= 1)
);

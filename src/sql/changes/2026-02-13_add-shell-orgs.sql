ALTER TABLE organizations
DROP CONSTRAINT org_tier_seat_rules,
DROP COLUMN max_seats,
ADD COLUMN is_shell_org boolean NOT NULL DEFAULT FALSE;

-- Set existing tier1 orgs to is_shell_org = TRUE before adding the constraint
UPDATE organizations
SET is_shell_org = TRUE
WHERE subscription_tier IN ('tier1_free_registered', 'tier1_basic_paid');

ALTER TABLE organizations
ADD CONSTRAINT org_shell_matches_tier CHECK (
  (subscription_tier IN ('tier1_free_registered', 'tier1_basic_paid') AND is_shell_org = TRUE)
  OR
  (subscription_tier IN ('tier2_pro', 'tier3_enterprise') AND is_shell_org = FALSE)
);

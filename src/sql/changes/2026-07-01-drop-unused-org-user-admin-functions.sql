-- Drops org/user admin functions whose only callers were the unwired endpoints
-- remove-org-user, update-org-user-role and update-user-org-membership-status --
-- removed to reduce attack surface (PYR1-1512 IDOR review).
DROP FUNCTION IF EXISTS remove_org_user(integer);
DROP FUNCTION IF EXISTS update_org_user_role(integer, text);
DROP FUNCTION IF EXISTS update_org_membership_status(integer, text);

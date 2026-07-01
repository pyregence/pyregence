-- Drops org/user admin functions whose only callers were the unwired endpoints
-- add-org-user (singular), remove-org-user, update-org-user-role,
-- update-user-org-membership-status and update-user-name -- removed to reduce
-- attack surface (PYR1-1512 IDOR review). can_admin_user backed those handlers'
-- per-resource authorization and now has no remaining caller.
-- add_org_user and update_user_name are intentionally kept: they are still used
-- by add-new-user and update-own-user-name respectively.
DROP FUNCTION IF EXISTS can_admin_user(integer, integer);
DROP FUNCTION IF EXISTS remove_org_user(integer);
DROP FUNCTION IF EXISTS update_org_user_role(integer, text);
DROP FUNCTION IF EXISTS update_org_membership_status(integer, text);

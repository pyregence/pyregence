-- Add the listed (existing) users to the "Pyregence Consortium" organization
-- as organization admins.
UPDATE users
SET organization_rid      = (SELECT organization_uid
                             FROM organizations
                             WHERE org_unique_id = 'pyregence-consortium'),
    user_role             = 'organization_admin',
    org_membership_status = 'accepted'
WHERE email IN (
    'sromsos@sig-gis.com',
    'chris@cloudfire.ai',
    'chris@cloudfire.com',
    'chris.lautenberger@gmail.com',
    'dsaah@sig-gis.com',
    'katy@pyrecast.com',
    'kbeehler@sig-gis.com',
    'jbui@sig-gis.com',
    'maria@cloudfire.com',
    'theodori@reaxengineering.com',
    'dsilva@sig-gis.com'
);

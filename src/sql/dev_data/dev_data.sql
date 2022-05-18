-- NAMESPACE: dev-data

-- Adds an organization called "Development"
INSERT INTO organizations
    (organization_uid, org_name, email_domains, auto_add, auto_accept)
VALUES
    (1, 'Development', '@pyr.dev', TRUE, TRUE);

-- Adds an admin and a user
INSERT INTO users
    (user_uid, email, name, password, verified, settings)
VALUES
    (1, 'admin@pyr.dev', 'Admin', crypt('admin', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (2, 'user@pyr.dev', 'User', crypt('user', gen_salt('bf')), TRUE, '{:timezone :utc}');

-- Adds the admin and user to the "Development" organization
INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (1, 1, 1),
    (1, 2, 2);

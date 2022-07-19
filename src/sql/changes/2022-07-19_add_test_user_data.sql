-- NAMESPACE: 2002-07-19-add-test-user-data

-- Adds test organizations: 'Delete Me Inc.', 'Update My Settings and Company', 'Acme Labs', and 'Skunk Works'
-- Adds test users with a mix of roles, and admin user that spans all test orgs: admin@all-orgs.com

INSERT INTO organizations
    (organization_uid, org_name, email_domains, auto_add, auto_accept)
VALUES
    (20, 'Delete Me Inc.', '@deleteme.com,@remove.com', TRUE, FALSE),
    (21, 'Update My Setttings and Company', '@updateme.com', FALSE, TRUE),
    (22, 'Acme Labs', '@acme.com,@acmelabs.org', TRUE, TRUE),
    (23, 'Skunk Works', '@topsecret.net,@seentoomuch.us,@zzyzx.gov,', FALSE, FALSE);

INSERT INTO users
    (user_uid, email, name, password, verified, settings)
VALUES
    (50, 'irmamorales@zzyz.gov', 'Irma Morales', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (51, 'constanceamaral@acme.com', 'Constance Amaral', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (52, 'vincentcarmona@updateme.com', 'Vincent Carmona', crypt('test456', gen_salt('bf')), true, '{:timezone :utc}'),
    (53, 'elizabethpinto@updateme.com', 'Elizabeth Pinto', crypt('test456', gen_salt('bf')), true, '{:timezone :utc}'),
    (54, 'shadmakinde@acme.com', 'Shad Makinde', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (55, 'viviensoares@acme.com', 'Vivien Soares', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (56, 'keelyigbinoghene@updateme.com', 'Keely Igbinoghene', crypt('test456', gen_salt('bf')), true, '{:timezone :utc}'),
    (57, 'sachabauer@acmelabs.com', 'Sacha Bauer', crypt('test456', gen_salt('bf')), true, '{:timezone :utc}'),
    (58, 'staceychan@acmelabs.org', 'Stacey Chan', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (59, 'kiarapichler@deleteme.com', 'Kiara Pichler', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (60, 'solomonlandry@topsecret.net', 'Solomon Landry', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (61, 'clintoncurtis@remove.com', 'Clinton Curtis', crypt('test123', gen_salt('bf')), FALSE, '{:timezone :utc}'),
    (62, 'tanaduval@zzyz.gov', 'Tana Duval', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (63, 'brandenbeaulieu@topsecret.net', 'Branden Beaulieu', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (64, 'bryarlogan@remove.com', 'Bryar Logan', crypt('test123', gen_salt('bf')), FALSE, '{:timezone :utc}'),
    (65, 'shaeleighparsons@deleteme.com', 'Shaeleigh Parsons', crypt('test123', gen_salt('bf')), FALSE, '{:timezone :utc}'),
    (66, 'quinnkoopman@updateme.com', 'Quinn Koopman', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (67, 'brendanwalsh@updateme.com', 'Brendan Walsh', crypt('test123', gen_salt('bf')), FALSE, '{:timezone :utc}'),
    (68, 'octaviusbalarabe@self.com', 'Octavius Balarabe', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (69, 'admin@all-orgs.com', 'Dwight Schrute', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}');
INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (20, 59, 1),
    (20, 61, 3),
    (20, 64, 2),
    (20, 65, 2),
    (20, 69, 1),
    (21, 52, 1),
    (21, 53, 1),
    (21, 56, 1),
    (21, 66, 2),
    (21, 69, 1),
    (22, 51, 3),
    (22, 54, 2),
    (22, 55, 1),
    (22, 57, 1),
    (22, 58, 1),
    (22, 68, 2),
    (22, 69, 1),
    (23, 50, 1),
    (23, 60, 2),
    (23, 62, 3),
    (23, 63, 3),
    (23, 68, 1),
    (23, 69, 1);

-- NAMESPACE: 2002-08-04-add-test-user-data

-- Adds test organizations: 'Delete Me Inc.', 'Update My Settings and Company', 'Acme Labs', and 'Skunk Works'
-- Adds test users with a mix of roles, and admin user that spans all test orgs: admin@all-orgs.com

-- "Delete Me Inc." --------------------------------------------------------------------------------
INSERT INTO organizations
    (org_name, email_domains, auto_add, auto_accept)
VALUES
    ('Delete Me Inc.', '@deleteme.com,@remove.com', TRUE, FALSE);
CALL public.add_org_test_user('Delete Me Inc.', 'kiarapichler@deleteme.com', 'Kiara Pichler',         crypt('test456', gen_salt('bf')), TRUE, 1);
CALL public.add_org_test_user('Delete Me Inc.', 'clintoncurtis@remove.com', 'Clinton Curtis',         crypt('test456', gen_salt('bf')), FALSE, 3);
CALL public.add_org_test_user('Delete Me Inc.', 'bryarlogan@remove.com', 'Bryar Logan',               crypt('test456', gen_salt('bf')), FALSE, 2);
CALL public.add_org_test_user('Delete Me Inc.', 'shaeleighparsons@deleteme.com', 'Shaeleigh Parsons', crypt('test123', gen_salt('bf')), FALSE, 2);
CALL public.add_org_test_user('Delete Me Inc.', 'admin@all-orgs.com', 'Dwight Schrute',               crypt('test456', gen_salt('bf')), TRUE, 1);

-- "Update My Settings and Company" ----------------------------------------------------------------
INSERT INTO organizations
    (org_name, email_domains, auto_add, auto_accept)
VALUES
    ('Update My Setttings and Company', '@updateme.com', FALSE, TRUE);
CALL public.add_org_test_user('Update My Setttings and Company', 'vincentcarmona@updateme.com', 'Vincent Carmona',     crypt('test456', gen_salt('bf')), true, 1);
CALL public.add_org_test_user('Update My Setttings and Company', 'elizabethpinto@updateme.com', 'Elizabeth Pinto',     crypt('test456', gen_salt('bf')), true, 1);
CALL public.add_org_test_user('Update My Setttings and Company', 'keelyigbinoghene@updateme.com', 'Keely Igbinoghene', crypt('test456', gen_salt('bf')), true, 1);
CALL public.add_org_test_user('Update My Setttings and Company', 'quinnkoopman@updateme.com', 'Quinn Koopman',         crypt('test456', gen_salt('bf')), TRUE, 2);

-- "Acme Labs" -------------------------------------------------------------------------------------
INSERT INTO organizations
    (org_name, email_domains, auto_add, auto_accept)
VALUES
    ('Acme Labs', '@acme.com,@acmelabs.org', TRUE, TRUE);
CALL public.add_org_test_user('Acme Labs', 'constanceamaral@acme.com', 'Constance Amaral', crypt('test456', gen_salt('bf')), TRUE, 3);
CALL public.add_org_test_user('Acme Labs', 'shadmakinde@acme.com', 'Shad Makinde',         crypt('test456', gen_salt('bf')), TRUE, 2);
CALL public.add_org_test_user('Acme Labs', 'viviensoares@acme.com', 'Vivien Soares',       crypt('test456', gen_salt('bf')), TRUE, 1);
CALL public.add_org_test_user('Acme Labs', 'sachabauer@acmelabs.com', 'Sacha Bauer',       crypt('test456', gen_salt('bf')), true, 1);
CALL public.add_org_test_user('Acme Labs', 'staceychan@acmelabs.org', 'Stacey Chan',       crypt('test456', gen_salt('bf')), TRUE, 1);

-- "Skunk Works" -----------------------------------------------------------------------------------
INSERT INTO organizations
    (org_name, email_domains, auto_add, auto_accept)
VALUES
    ('Skunk Works', '@topsecret.net,@seentoomuch.us,@zzyzx.gov', FALSE, FALSE);
CALL public.add_org_test_user('Skunk Works', 'irmamorales@zzyz.gov', 'Irma Morales',              crypt('test456', gen_salt('bf')), TRUE, 1);
CALL public.add_org_test_user('Skunk Works', 'solomonlandry@topsecret.net', 'Solomon Landry',     crypt('test456', gen_salt('bf')), TRUE, 2);
CALL public.add_org_test_user('Skunk Works', 'tanaduval@zzyz.gov', 'Tana Duval',                  crypt('test456', gen_salt('bf')), TRUE, 3);
CALL public.add_org_test_user('Skunk Works', 'brandenbeaulieu@topsecret.net', 'Branden Beaulieu', crypt('test456', gen_salt('bf')), TRUE, 3);
CALL public.add_org_test_user('Skunk Works', 'octaviusbalarabe@self.com', 'Octavius Balarabe',    crypt('test456', gen_salt('bf')), TRUE, 1);

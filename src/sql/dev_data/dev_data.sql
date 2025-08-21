-- NAMESPACE: dev-data

INSERT INTO organizations
    (organization_uid, org_unique_id, org_name, email_domains, auto_add, auto_accept)
VALUES
    (1, 'development', 'Development', '@pyr.dev', TRUE, TRUE),
    (2, 'delete-me', 'Delete Me Inc.', '@deleteme.com,@remove.com', TRUE, FALSE),
    (3, 'update-me', 'Update My Setttings and Company', '@updateme.com', FALSE, TRUE),
    (4, 'sig', 'Spatial Informatics Group', '@sig-gis.com', TRUE, TRUE),
    (5, 'pyregence', 'Pyregence Consortium', '', FALSE, FALSE);

INSERT INTO users
    (user_uid, email, name, password, email_verified, match_drop_access, settings, user_role, org_membership_status, organization_rid)
VALUES
    (1, 'admin@pyr.dev',                  'Admin',             crypt('admin', gen_salt('bf')),    TRUE,  TRUE,  '{:timezone :utc}',                     'organization_admin',  'accepted', 1),
    (2, 'user@pyr.dev',                   'User',              crypt('user', gen_salt('bf')),     TRUE,  FALSE, '{:timezone :utc}',                     'organization_member', 'accepted', 1),
    (3, 'account_manager@pyr.dev',        'Account Manager',   crypt('manager', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'account_manager',     'none',     NULL),
    (4, 'testuser@sig-gis.com',           'Test SIG User',     crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'organization_member', 'accepted', 4),
    (5, 'vincentcarmona@updateme.com',    'Vincent Carmona',   crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'organization_admin',  'accepted', 3),
    (6, 'elizabethpinto@updateme.com',    'Elizabeth Pinto',   crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'organization_admin',  'accepted', 3),
    (7, 'pyr_member@member.com',          'Member',            crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'member',              'none',     NULL),
    (8, 'viviensoares@acme.com',          'Vivien Soares',     crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'organization_admin',  'accepted', 4),
    (9, 'keelyigbinoghene@updateme.com',  'Keely Igbinoghene', crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'organization_admin',  'accepted', 3),
    (10, 'sachabauer@acmelabs.com',       'Sacha Bauer',       crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'organization_admin',  'accepted', 4),
    (11, 'staceychan@acmelabs.org',       'Stacey Chan',       crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'organization_admin',  'accepted', 4),
    (12, 'kiarapichler@deleteme.com',     'Kiara Pichler',     crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'organization_admin',  'accepted', 2),
    (13, 'solomonlandry@topsecret.net',   'Solomon Landry',    crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'organization_member', 'accepted', 5),
    (14, 'clintoncurtis@remove.com',      'Clinton Curtis',    crypt('test123', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'organization_member', 'accepted', 2),
    (15, 'tanaduval@zzyz.gov',            'Tana Duval',        crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'member',              'none',     NULL),
    (16, 'brandenbeaulieu@topsecret.net', 'Branden Beaulieu',  crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'member',              'none',     NULL),
    (17, 'bryarlogan@remove.com',         'Bryar Logan',       crypt('test123', gen_salt('bf')),  FALSE, FALSE, '{:timezone :utc}',                     'organization_member', 'pending',  2),
    (18, 'shaeleighparsons@deleteme.com', 'Shaeleigh Parsons', crypt('test123', gen_salt('bf')),  FALSE, FALSE, '{:timezone :utc}',                     'organization_member', 'accepted', 2),
    (19, 'quinnkoopman@updateme.com',     'Quinn Koopman',     crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'organization_member', 'accepted', 3),
    (20, 'unverified_email@gmail.com',    'Unverified Email',  crypt('test123', gen_salt('bf')),  FALSE, FALSE, '{:timezone :utc}',                     'member',              'none',     NULL),
    (21, 'octaviusbalarabe@self.com',     'Octavius Balarabe', crypt('test456', gen_salt('bf')),  TRUE,  FALSE, '{:timezone :utc}',                     'organization_member', 'accepted', 4),
    (22, 'admin@all-orgs.com',            'Dwight Schrute',    crypt('test456', gen_salt('bf')),  TRUE,  TRUE,  '{:timezone :utc}',                     'super_admin',         'none',     NULL),
    (23, 'email-2fa@pyr.dev',             'Email 2FA User',    crypt('email2fa', gen_salt('bf')), TRUE,  TRUE,  '{:timezone :utc, :two-factor :email}', 'organization_member', 'accepted', 1),
    (24, 'totp-2fa@pyr.dev',              'TOTP 2FA User',     crypt('totp2fa', gen_salt('bf')),  TRUE,  TRUE,  '{:timezone :utc, :two-factor :totp}',  'organization_member', 'accepted', 1);

-- TOTP setup for totp-2fa@pyr.dev user
-- Secret: JBSWY3DPEHPK3PXP (base32 encoded test secret)
INSERT INTO user_totp (user_id, secret, verified)
VALUES (24, 'JBSWY3DPEHPK3PXP', TRUE);

-- Backup codes for TOTP test user
INSERT INTO user_backup_codes (user_id, code)
VALUES
    (24, 'ABC12345'),
    (24, 'DEF67890'),
    (24, 'GHI13579'),
    (24, 'JKL24680'),
    (24, 'MNO36912'),
    (24, 'PQR48024'),
    (24, 'STU59136'),
    (24, 'VWX60247');

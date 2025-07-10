-- NAMESPACE: dev-data

INSERT INTO organizations
    (organization_uid, org_unique_id, org_name, email_domains, auto_add, auto_accept)
VALUES
    (1, 'development', 'Development', '@pyr.dev', TRUE, TRUE),
    (2, 'delete-me', 'Delete Me Inc.', '@deleteme.com,@remove.com', TRUE, FALSE),
    (3, 'update-me', 'Update My Setttings and Company', '@updateme.com', FALSE, TRUE),
    (4, 'acme-labs', 'Acme Labs', '@acme.com,@acmelabs.org', TRUE, TRUE),
    (5, 'skunk-works', 'Skunk Works', '@topsecret.net,@seentoomuch.us,@zzyzx.gov,', FALSE, FALSE);

INSERT INTO users
    (user_uid, email, name, password, verified, settings)
VALUES
    (1,  'admin@pyr.dev', 'Admin', crypt('admin', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (2,  'user@pyr.dev', 'User', crypt('user', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (3,  'irmamorales@zzyz.gov', 'Irma Morales', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (4,  'constanceamaral@acme.com', 'Constance Amaral', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (5,  'vincentcarmona@updateme.com', 'Vincent Carmona', crypt('test456', gen_salt('bf')), true, '{:timezone :utc}'),
    (6,  'elizabethpinto@updateme.com', 'Elizabeth Pinto', crypt('test456', gen_salt('bf')), true, '{:timezone :utc}'),
    (7,  'shadmakinde@acme.com', 'Shad Makinde', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (8,  'viviensoares@acme.com', 'Vivien Soares', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (9,  'keelyigbinoghene@updateme.com', 'Keely Igbinoghene', crypt('test456', gen_salt('bf')), true, '{:timezone :utc}'),
    (10, 'sachabauer@acmelabs.com', 'Sacha Bauer', crypt('test456', gen_salt('bf')), true, '{:timezone :utc}'),
    (11, 'staceychan@acmelabs.org', 'Stacey Chan', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (12, 'kiarapichler@deleteme.com', 'Kiara Pichler', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (13, 'solomonlandry@topsecret.net', 'Solomon Landry', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (14, 'clintoncurtis@remove.com', 'Clinton Curtis', crypt('test123', gen_salt('bf')), FALSE, '{:timezone :utc}'),
    (15, 'tanaduval@zzyz.gov', 'Tana Duval', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (16, 'brandenbeaulieu@topsecret.net', 'Branden Beaulieu', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (17, 'bryarlogan@remove.com', 'Bryar Logan', crypt('test123', gen_salt('bf')), FALSE, '{:timezone :utc}'),
    (18, 'shaeleighparsons@deleteme.com', 'Shaeleigh Parsons', crypt('test123', gen_salt('bf')), FALSE, '{:timezone :utc}'),
    (19, 'quinnkoopman@updateme.com', 'Quinn Koopman', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (20, 'brendanwalsh@updateme.com', 'Brendan Walsh', crypt('test123', gen_salt('bf')), FALSE, '{:timezone :utc}'),
    (21, 'octaviusbalarabe@self.com', 'Octavius Balarabe', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}'),
    (22, 'admin@all-orgs.com', 'Dwight Schrute', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}');
    (23, 'email-2fa@pyr.dev', 'Email 2FA User', crypt('email2fa', gen_salt('bf')), TRUE, '{:timezone :utc, :two-factor :email}'),
    (24, 'totp-2fa@pyr.dev', 'TOTP 2FA User', crypt('totp2fa', gen_salt('bf')), TRUE, '{:timezone :utc, :two-factor :totp}'),

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (1, 1, 1),
    (1, 2, 2),
    (1, 23, 2),
    (1, 24, 2),
    (2, 12, 1),
    (2, 14, 3),
    (2, 17, 2),
    (2, 18, 2),
    (2, 22, 1),
    (3, 5, 1),
    (3, 6, 1),
    (3, 9, 1),
    (3, 19, 2),
    (3, 22, 1),
    (4, 4, 3),
    (4, 7, 2),
    (4, 8, 1),
    (4, 10, 1),
    (4, 11, 1),
    (4, 21, 2),
    (4, 22, 1),
    (5, 3, 1),
    (5, 13, 2),
    (5, 15, 3),
    (5, 16, 3),
    (5, 21, 1),
    (5, 22, 1);

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

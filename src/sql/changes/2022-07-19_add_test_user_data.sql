-- NAMESPACE: 2002-07-19-add-test-user-data

-- Adds test organizations: 'Delete Me Inc.', 'Update My Settings and Company', 'Acme Labs', and 'Skunk Works'
-- Adds test users with a mix of roles, and admin user that spans all test orgs: admin@all-orgs.com

-- "Delete Me Inc." --------------------------------------------------------------------------------
INSERT INTO organizations
    (org_name, email_domains, auto_add, auto_accept)
VALUES
    ('Delete Me Inc.', '@deleteme.com,@remove.com', TRUE, FALSE) RETURNING organization_uid INTO org-id;

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('kiarapichler@deleteme.com', 'Kiara Pichler', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 1);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('clintoncurtis@remove.com', 'Clinton Curtis', crypt('test123', gen_salt('bf')), FALSE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 3);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('bryarlogan@remove.com', 'Bryar Logan', crypt('test123', gen_salt('bf')), FALSE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 2);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('shaeleighparsons@deleteme.com', 'Shaeleigh Parsons', crypt('test123', gen_salt('bf')), FALSE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 2);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('admin@all-orgs.com', 'Dwight Schrute', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 1);

-- "Update My Settings and Company" ----------------------------------------------------------------
INSERT INTO organizations
    (org_name, email_domains, auto_add, auto_accept)
VALUES
    ('Update My Setttings and Company', '@updateme.com', FALSE, TRUE) RETURNING org-id;

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('vincentcarmona@updateme.com', 'Vincent Carmona', crypt('test456', gen_salt('bf')), true, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 1);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('elizabethpinto@updateme.com', 'Elizabeth Pinto', crypt('test456', gen_salt('bf')), true, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 1);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('keelyigbinoghene@updateme.com', 'Keely Igbinoghene', crypt('test456', gen_salt('bf')), true, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 1);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('quinnkoopman@updateme.com', 'Quinn Koopman', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 2);

---

WITH assistant-admin-schrute AS (SELECT user_uid FROM users WHERE email = 'admin@all-orgs.com')
INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, (SELECT user_uid FROM assistant-admin-schrute), 1);

-- "Acme Labs" -------------------------------------------------------------------------------------

INSERT INTO organizations
    (org_name, email_domains, auto_add, auto_accept)
VALUES
    ('Acme Labs', '@acme.com,@acmelabs.org', TRUE, TRUE) RETURNING org-id;

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('constanceamaral@acme.com', 'Constance Amaral', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 3);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('shadmakinde@acme.com', 'Shad Makinde', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 2);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('viviensoares@acme.com', 'Vivien Soares', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 1);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('sachabauer@acmelabs.com', 'Sacha Bauer', crypt('test456', gen_salt('bf')), true, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 1);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('staceychan@acmelabs.org', 'Stacey Chan', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 1);

---

WITH assistant-admin-schrute AS (SELECT user_uid FROM users WHERE email = 'admin@all-orgs.com')
INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, (SELECT user_uid FROM assistant-admin-schrute), 1);

-- "Skunk Works" -----------------------------------------------------------------------------------

INSERT INTO organizations
    (org_name, email_domains, auto_add, auto_accept)
VALUES
    ('Skunk Works', '@topsecret.net,@seentoomuch.us,@zzyzx.gov', FALSE, FALSE) RETURNING organization_uid INTO org-id;

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('irmamorales@zzyz.gov', 'Irma Morales', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 1);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('solomonlandry@topsecret.net', 'Solomon Landry', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 2);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('tanaduval@zzyz.gov', 'Tana Duval', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 3);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('brandenbeaulieu@topsecret.net', 'Branden Beaulieu', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 3);

---

INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('octaviusbalarabe@self.com', 'Octavius Balarabe', crypt('test456', gen_salt('bf')), TRUE, '{:timezone :utc}') RETURNING user_uid INTO user-id;

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, user-id, 1);

---

WITH assistant-admin-schrute AS (SELECT user_uid FROM users WHERE email = 'admin@all-orgs.com')
INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (org-id, (SELECT user_uid FROM assistant-admin-schrute), 1);

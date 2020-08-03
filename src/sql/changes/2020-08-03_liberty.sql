INSERT INTO roles
    (role_uid, title)
VALUES
    (1, 'Admin'),
    (2, 'Member'),
    (3, 'Pending');

INSERT INTO users
    (email, name, password, settings)
VALUES
    ('liberty-demo', 'Liberty', crypt('#liberty2019#', gen_salt('bf')), '{:theme :dark, :timezone :utc}');

INSERT INTO organizations
    (org_name, email_domains, settings)
VALUES
    -- 3
    ('Liberty', '[@libery.com]', '{:auto-add? false, :auto-accept? false}');

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (2, 1, 1),
    (2, 2, 1),
    (2, 3, 1),
    (3, 4, 1);

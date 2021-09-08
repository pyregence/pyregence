-- NAMESPACE: default

INSERT INTO roles
    (role_uid, title)
VALUES
    (1, 'Admin'),
    (2, 'Member'),
    (3, 'Pending');

INSERT INTO organizations
    (org_name, email_domains, auto_add, auto_accept)
VALUES
    -- 1
    ('Public', '', FALSE, FALSE),
    -- 2
    ('Pyregence Consortium', '@sig-gis.com,@reaxengineering.com', TRUE, TRUE),
    -- 3
    ('Liberty', '@libertyutilities.com', TRUE, TRUE),
    -- 4
    ('PacifiCorp', '@pacificorp.com', TRUE, TRUE);

INSERT INTO users
    (email, name, password, settings)
VALUES
    -- 1
    ('mspencer@sig-gis.com', 'Matt Spencer', 'not-a-password', '{:theme :dark, :timezone :utc}'),
    -- 2
    ('gjohnson@sig-gis.com', 'Gary Johnson', 'not-a-password', '{:theme :dark, :timezone :utc}'),
    -- 3
    ('lautenberger@reaxengineering.com', 'Chris Lautenberger', 'not-a-password', '{:theme :dark, :timezone :utc}'),
    -- 4 TODO remove this account and have liberty users add themselves
    ('liberty-demo', 'Liberty Demo Account', crypt('#liberty2019#', gen_salt('bf')), '{:theme :dark, :timezone :utc}'),
    -- 5
    ('rsheperd@sig-gis.com', 'RJ Sheperd', 'not-a-password', '{:theme :dark, :timezone :utc}'),
    -- 6
    ('obaldwinedwards@sig-gis.com', 'Oliver Baldwin Edwards', 'not-a-password', '{:theme :dark, :timezone :utc}');


UPDATE users SET verified = TRUE WHERE email = 'liberty-demo';

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (2, 1, 1),
    (2, 2, 1),
    (2, 3, 1),
    (2, 5, 1),
    (2, 6, 1),
    (3, 1, 1),
    (3, 2, 1),
    (3, 3, 1),
    (3, 4, 1),
    (3, 5, 1),
    (3, 6, 1),
    (4, 1, 1),
    (4, 2, 1),
    (4, 3, 1),
    (4, 5, 1),
    (4, 6, 1);

INSERT INTO organization_layers
    (organization_rid, layer_path, layer_config)
VALUES
    (3, '[:fire-risk :params :pattern :options :liberty]', '{:opt-label "Liberty Distribution Lines", :filter "liberty", :clear-point? true}'),
    (4, '[:fire-risk :params :pattern :options :pacificorp]', '{:opt-label "PacifiCorp Distribution Lines", :filter "pacificorp", :clear-point? true}');

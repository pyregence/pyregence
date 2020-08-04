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
    ('Pyregence Consortium', '["@sig-gis.com", "@reaxengineering.com"]', TRUE, TRUE),
    -- 3
    ('Liberty' , '["@libertyutilities.com"]', TRUE, TRUE),
    -- 4
    ('PacifiCorp' , '["@pacificorp.com"]', TRUE, TRUE);

-- TODO dont set password
INSERT INTO users
    (email, name, password, settings)
VALUES
    -- 1
    ('mspencer@sig-gis.com', 'Matt Spencer', crypt('1234', gen_salt('bf')), '{:theme :dark, :timezone :utc}'),
    -- 2
    ('gjonson@sig-gis.com', 'Gary Johnson', crypt('1234', gen_salt('bf')), '{:theme :dark, :timezone :utc}'),
    -- 3
    ('lautenberger@reaxengineering.com', 'Chris Lautenberger', crypt('1234', gen_salt('bf')), '{:theme :dark, :timezone :utc}'),
    --4
    ('test@email.com', 'Test', crypt('1234', gen_salt('bf')), ''),
    --5
    ('liberty-test', 'Liberty Test', crypt('1234', gen_salt('bf')), '');

INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    (2, 1, 1),
    (2, 2, 1),
    (2, 3, 1),
    (3, 1, 1),
    (3, 2, 1),
    (3, 3, 1),
    (3, 5, 1),
    (4, 1, 1),
    (4, 2, 1),
    (4, 3, 2);

INSERT INTO organization_layers
    (organization_rid, layer_path, layer_config)
VALUES
    (3, '[:fire-risk :params :pattern :options :liberty]', '{:opt-label "Liberty Distribution Lines", :filter "liberty", :clear-point? true}'),
    (4, '[:fire-risk :params :pattern :options :pacificorp]', '{:opt-label "PacificCorp Distribution Lines", :filter "pacificorp", :clear-point? true}');

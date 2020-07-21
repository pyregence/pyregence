-- NAMESPACE: default

INSERT INTO organizations
    (org_name, email_domains, settings)
VALUES
    -- 1
    ('Public', '', '{:auto-add? false, :auto-accept? flase'),
    -- 2
    ('Pyregence Consortium', '' '{:auto-add? false, :auto-accept?');

INSERT INTO users
    (email, name, password, settings)
VALUES
    ('mspencer@sig-gis.com', 'Matt Spencer', 'changeme', '{:theme :dark, :timezone :utc}'),
    ('gjonson@sig-gis.com', 'Gary Johnson', 'changeme', '{:theme :dark, :timezone :utc}'),
    ('lautenberger@reaxengineering.com', 'Chris Lautenberger', 'changeme', '{:theme :dark :timezone :utc}');

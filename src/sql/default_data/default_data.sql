-- NAMESPACE: default

INSERT INTO pyre.organizations
    (org_name, settings)
VALUES
    -- 1
    ('Public', '{:auto-add? false :domains []'),
    -- 2
    ('Pyregence Consortium', '{:auto-add? false :domains []');

INSERT INTO pyre.users
    (email, name, password, settings)
VALUES
    ('mspencer@sig-gis.com', 'Matt Spencer', 'changeme', '{:theme :dark, :timezone :utc}'),
    ('gjonson@sig-gis.com', 'Gary Johnson', 'changeme', '{:theme :dark, :timezone :utc}'),
    ('lautenberger@reaxengineering.com', 'Chris Lautenberger', 'changeme', '{:theme :dark :timezone :utc}');

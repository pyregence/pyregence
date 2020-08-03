-- NAMESPACE: default

INSERT INTO roles
    (role_uid, title)
VALUES
    (1, 'Admin'),
    (2, 'Member'),
    (3, 'Pending');

INSERT INTO organizations
    (org_name, email_domains, settings)
VALUES
    -- 1
    ('Public', '', '{:auto-add? false, :auto-accept? false}'),
    -- 2
    ('Pyregence Consortium', '', '{:auto-add? false, :auto-accept? false}');

INSERT INTO users
    (email, name, password, settings)
VALUES

INSERT INTO organization_layers
    (organization_rid, layer_path, layer_config)
VALUES
    (3, '[:fire-risk :params :pattern :options :liberty]', '{:opt-label "Liberty Distribution Lines", :filter "liberty", :clear-point? true}'),
    (4, '[:fire-risk :params :pattern :options :pacificorp]', '{:opt-label "PacificCorp Distribution Lines", :filter "pacificorp", :clear-point? true}');

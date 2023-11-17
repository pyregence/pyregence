ALTER TABLE users
ADD psps_org text;

UPDATE organizations
SET org_unique_id = 'nve'
WHERE org_name = 'NV Energy';

UPDATE users
set psps_org = 'nve'
WHERE email = 'demo@nvenergy.com';

UPDATE users
set email = 'demo@liberty.com'
WHERE name = 'Liberty Demo Account';

UPDATE users
set psps_org = 'liberty'
WHERE email = 'demo@liberty.com';

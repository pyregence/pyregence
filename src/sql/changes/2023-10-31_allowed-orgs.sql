ALTER TABLE organizations
ADD org_unique_id text UNIQUE;

UPDATE organizations
SET org_unique_id = 'public'
WHERE org_name = 'Public';

UPDATE organizations
SET org_unique_id = 'nv-energy'
WHERE org_name = 'NV Energy';

UPDATE organizations
SET org_unique_id = 'pyregence-consortium'
WHERE org_name = 'Pyregence Consortium';


UPDATE organizations
SET org_unique_id = 'liberty'
WHERE org_name = 'Liberty';

UPDATE organizations
SET org_unique_id = 'pacificorp'
WHERE org_name = 'PacifiCorp';

ALTER TABLE organizations
ALTER COLUMN org_unique_id SET NOT NULL;

ALTER TABLE users
ADD match_drop_access boolean DEFAULT FALSE;

UPDATE users
SET match_drop_access = TRUE
WHERE email = 'admin@all-orgs.com';

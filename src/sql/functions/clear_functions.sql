-- NAMESPACE: clear

WITH funcs AS (
  SELECT
    format('DROP FUNCTION %s;', p.oid::regprocedure) AS drop_statement
  FROM pg_proc p
  JOIN pg_roles r ON p.proowner = r.oid
  WHERE r.rolname = :'user'
    AND p.prokind = 'f'
)
SELECT drop_statement FROM funcs \gexec

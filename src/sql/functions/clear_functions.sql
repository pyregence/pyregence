-- NAMESPACE: clear

-- Drop all functions owned by the current user
WITH funcs AS (
  SELECT
    format('DROP FUNCTION %s CASCADE;', p.oid::regprocedure) AS drop_statement
  FROM pg_proc p
  JOIN pg_roles r ON p.proowner = r.oid
  WHERE r.rolname = :'user'
    AND p.prokind = 'f'
)
SELECT drop_statement FROM funcs \gexec

-- Drop all triggers owned by the current user
WITH triggers AS (
  SELECT DISTINCT
    format('DROP TRIGGER IF EXISTS %I ON %I.%I RESTRICT;',
           t.tgname, 
           n.nspname, 
           c.relname) AS drop_statement
  FROM pg_trigger t
  JOIN pg_class c ON t.tgrelid = c.oid
  JOIN pg_namespace n ON c.relnamespace = n.oid
  JOIN pg_proc p ON t.tgfoid = p.oid
  JOIN pg_roles r ON p.proowner = r.oid
  WHERE r.rolname = :'user'
    AND NOT t.tgisinternal
)
SELECT drop_statement FROM triggers \gexec

-- NAMESPACE: clear

DO $$
 DECLARE
   _sql text;
 BEGIN
    SELECT INTO _sql
        string_agg(format('DROP FUNCTION %s;', oid::regprocedure), E'\n')
    FROM pg_proc
    WHERE (proowner = 'pyregence'::regrole)
        AND prokind = 'f';

    IF _sql IS NOT NULL THEN
        EXECUTE _sql;
    ELSE
        RAISE NOTICE 'No functions found.';
    END IF;
 END
$$ LANGUAGE plpgsql;

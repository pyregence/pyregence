-- PYR1-1097: record which model a match drop ran.
-- 'standard' is ELMFIRE + Pyretechnics, 'cawfe' is the coupled fire-atmosphere model.
ALTER TABLE match_jobs ADD COLUMN model text DEFAULT 'standard';

-- Reload the functions after this (`bb functions`): the running-job guard and the
-- job readers change signature, and `clear_functions.sql` drops the old ones first.

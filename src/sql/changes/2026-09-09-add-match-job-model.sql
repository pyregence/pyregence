-- PYR1-1097: record which model a match drop ran.
-- 'landfire' is ELMFIRE + Pyretechnics, 'cawfe' is the coupled fire-atmosphere model.
ALTER TABLE match_jobs ADD COLUMN model text DEFAULT 'landfire';

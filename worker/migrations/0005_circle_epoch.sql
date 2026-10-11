-- v2.6 server-side circle epoch (docs/feat/circle-accounts.md § Epoch). Additive: existing rows get NULL, which means
-- "no 2.6 owner app has written yet" and keeps 2.5 owner apps working until one does. Apply before deploying 2.6:
-- the 2.6 worker selects this column.
ALTER TABLE circles ADD COLUMN epoch INTEGER;

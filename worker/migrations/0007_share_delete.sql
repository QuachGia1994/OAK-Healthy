-- v2.8 share delete (docs/feat/delete-everything.md § Share links). Additive: shares made before 2.8 keep NULL and
-- cannot be deleted; 2.7 workers name their INSERT columns, so applying this first is safe.
ALTER TABLE shares ADD COLUMN delete_hash TEXT;

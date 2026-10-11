-- v2.7 vault tombstone (docs/feat/delete-everything.md). Additive: existing rows get NULL (live vault). Delete
-- everything empties the blob and sets deleted_at; GET and PUT then answer 410. Apply before deploying 2.7: the 2.7
-- worker selects this column.
ALTER TABLE vaults ADD COLUMN deleted_at INTEGER;

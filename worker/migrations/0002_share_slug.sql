-- v2.2: readable share URLs (/s/<handle>/<slug>) and item count for link previews. Run once on existing databases.
ALTER TABLE shares ADD COLUMN slug TEXT;
ALTER TABLE shares ADD COLUMN items INTEGER;
CREATE UNIQUE INDEX IF NOT EXISTS shares_slug ON shares (slug);

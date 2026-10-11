-- v2.3 reminder snooze: one pending one-off ping per push endpoint (epoch ms, minute-aligned).
ALTER TABLE push_subs ADD COLUMN snooze_at INTEGER;
CREATE INDEX IF NOT EXISTS push_subs_snooze ON push_subs (snooze_at);

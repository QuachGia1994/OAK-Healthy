-- v2.4 circle members with their own accounts (docs/feat/circle-accounts.md). join_hash NULL = legacy bearer circle.
ALTER TABLE circles ADD COLUMN join_hash TEXT;
CREATE TABLE IF NOT EXISTS member_keys (
  user_id TEXT PRIMARY KEY,
  pub TEXT NOT NULL,
  sealed TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS circle_members (
  circle_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  pub TEXT NOT NULL,
  proof TEXT NOT NULL,
  wrap TEXT,
  joined_at INTEGER NOT NULL,
  PRIMARY KEY (circle_id, user_id)
);
CREATE INDEX IF NOT EXISTS circle_members_user ON circle_members (user_id);

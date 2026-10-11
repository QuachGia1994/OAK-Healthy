CREATE TABLE IF NOT EXISTS vaults (
  id TEXT PRIMARY KEY,
  rev INTEGER NOT NULL,
  blob TEXT NOT NULL,
  auth_hash TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS shares (
  id TEXT PRIMARY KEY,
  blob TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  slug TEXT,
  items INTEGER
);
CREATE INDEX IF NOT EXISTS shares_expires ON shares (expires_at);
-- v2.2 pretty share URLs. Existing databases: ALTER TABLE shares ADD COLUMN slug TEXT; ALTER TABLE shares ADD COLUMN items INTEGER;
CREATE UNIQUE INDEX IF NOT EXISTS shares_slug ON shares (slug);
-- v2.1: accounts (zero-knowledge key wrapping), passkey wraps, push reminders, circles, rate limits.
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  salt TEXT,
  auth_hash TEXT,
  wrapped TEXT,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS identities (
  provider TEXT NOT NULL,
  subject TEXT NOT NULL,
  user_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (provider, subject)
);
CREATE INDEX IF NOT EXISTS identities_user ON identities (user_id);
CREATE TABLE IF NOT EXISTS sessions (
  id_hash TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS sessions_user ON sessions (user_id);
CREATE TABLE IF NOT EXISTS reset_tokens (
  token_hash TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  used INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS oauth_states (
  state TEXT PRIMARY KEY,
  provider TEXT NOT NULL,
  verifier TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS rate_limits (
  key TEXT PRIMARY KEY,
  count INTEGER NOT NULL,
  window_start INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS wrapped_keys (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  cred_id TEXT NOT NULL,
  prf_salt TEXT NOT NULL,
  blob TEXT NOT NULL,
  label TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS wrapped_keys_user ON wrapped_keys (user_id);
CREATE TABLE IF NOT EXISTS push_subs (
  id TEXT PRIMARY KEY,
  endpoint TEXT NOT NULL,
  minutes TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  snooze_at INTEGER
);
-- v2.3 reminder snooze. Existing databases: worker/migrations/0003_push_snooze.sql
CREATE INDEX IF NOT EXISTS push_subs_snooze ON push_subs (snooze_at);
CREATE TABLE IF NOT EXISTS circles (
  id TEXT PRIMARY KEY,
  blob TEXT NOT NULL,
  write_hash TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  join_hash TEXT,
  epoch INTEGER
);
-- v2.6 circle epoch (invite rotations; NULL until a 2.6 owner app writes). Existing databases: worker/migrations/0005_circle_epoch.sql
-- v2.4 circle members with their own accounts. Existing databases: worker/migrations/0004_circle_members.sql
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

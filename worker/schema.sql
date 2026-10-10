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
  expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS shares_expires ON shares (expires_at);
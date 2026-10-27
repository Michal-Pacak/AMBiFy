-- Stage 2: family, accounts, child profiles, invitations, signed-in devices. Times are epoch milliseconds.
CREATE TABLE organizations (
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE users (
    id              TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL REFERENCES organizations(id),
    name            TEXT NOT NULL,
    email           TEXT NOT NULL UNIQUE,
    password_hash   TEXT NOT NULL,
    role            TEXT NOT NULL,
    failed_logins   INTEGER NOT NULL DEFAULT 0,
    locked_until    INTEGER,
    module_order    TEXT,
    created_at      INTEGER NOT NULL
);

CREATE TABLE child_profiles (
    id              TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL REFERENCES organizations(id),
    name            TEXT NOT NULL,
    color           TEXT NOT NULL,
    created_at      INTEGER NOT NULL
);

CREATE TABLE invitations (
    code            TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL REFERENCES organizations(id),
    created_by      TEXT NOT NULL REFERENCES users(id),
    created_at      INTEGER NOT NULL,
    expires_at      INTEGER NOT NULL,
    used_by         TEXT REFERENCES users(id)
);

-- One row per signed-in device; the refresh token is stored only as a SHA-256 hash.
CREATE TABLE sessions (
    id           TEXT PRIMARY KEY,
    user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   TEXT NOT NULL UNIQUE,
    device_name  TEXT NOT NULL,
    created_at   INTEGER NOT NULL,
    last_used_at INTEGER NOT NULL
);

-- One-time codes a parent hands out when someone forgets the password (stored hashed).
CREATE TABLE password_resets (
    code_hash  TEXT PRIMARY KEY,
    user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at INTEGER NOT NULL
);

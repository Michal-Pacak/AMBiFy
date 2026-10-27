-- foreign_keys=off
-- Stage 3: a person can belong to many communities (role per community), the Platform Admin flag,
-- premium grants and modules switched off per community. Times are epoch milliseconds.
-- Rebuilding `users` needs foreign keys off (SQLite's table rebuild procedure); see Migrations.kt.

CREATE TABLE memberships (
    user_id         TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    role            TEXT NOT NULL,
    module_order    TEXT,
    joined_at       INTEGER NOT NULL,
    PRIMARY KEY (user_id, organization_id)
);

INSERT INTO memberships (user_id, organization_id, role, module_order, joined_at)
    SELECT id, organization_id, role, module_order, created_at FROM users;

CREATE TABLE users_new (
    id               TEXT PRIMARY KEY,
    name             TEXT NOT NULL,
    email            TEXT NOT NULL UNIQUE,
    password_hash    TEXT NOT NULL,
    failed_logins    INTEGER NOT NULL DEFAULT 0,
    locked_until     INTEGER,
    -- Above all communities: grants premium modules for free. Never set through the API.
    platform_admin   INTEGER NOT NULL DEFAULT 0,
    -- Communities this person may still found for free (handed out by the Platform Admin).
    founding_credits INTEGER NOT NULL DEFAULT 0,
    created_at       INTEGER NOT NULL
);

INSERT INTO users_new (id, name, email, password_hash, failed_logins, locked_until, created_at)
    SELECT id, name, email, password_hash, failed_logins, locked_until, created_at FROM users;

DROP TABLE users;

ALTER TABLE users_new RENAME TO users;

-- The oldest Owner on the server becomes the Platform Admin.
UPDATE users SET platform_admin = 1 WHERE id = (
    SELECT u.id FROM users u JOIN memberships m ON m.user_id = u.id
    WHERE m.role = 'OWNER' ORDER BY u.created_at LIMIT 1
);

ALTER TABLE organizations ADD COLUMN created_by TEXT;

-- 0 = the free first community of its Owner, 1 = paid for (or granted by the Platform Admin).
ALTER TABLE organizations ADD COLUMN paid_founding INTEGER NOT NULL DEFAULT 0;

UPDATE organizations SET created_by = (
    SELECT m.user_id FROM memberships m
    WHERE m.organization_id = organizations.id AND m.role = 'OWNER' LIMIT 1
);

-- Premium modules and features a community has; `source` is GRANTED (Platform Admin) or later PURCHASED.
CREATE TABLE organization_grants (
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    grant_key       TEXT NOT NULL,
    source          TEXT NOT NULL,
    granted_by      TEXT,
    granted_at      INTEGER NOT NULL,
    PRIMARY KEY (organization_id, grant_key)
);

-- Modules the Owner or an Admin switched off; everything owned is on by default.
CREATE TABLE disabled_modules (
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    module          TEXT NOT NULL,
    PRIMARY KEY (organization_id, module)
);

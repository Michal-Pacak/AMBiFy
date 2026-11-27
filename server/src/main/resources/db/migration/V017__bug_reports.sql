-- 0.7.9: bug reports sent from the app; screenshots are files next to the database (reports/<id>/<n>.jpg).
CREATE TABLE bug_reports (
    id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id TEXT REFERENCES organizations(id) ON DELETE SET NULL,
    text TEXT NOT NULL,
    screen TEXT,
    app_version TEXT NOT NULL,
    app_code INTEGER NOT NULL,
    phone TEXT NOT NULL,
    android TEXT NOT NULL,
    kids INTEGER NOT NULL DEFAULT 0,
    network TEXT,
    created_at INTEGER NOT NULL,
    received_at INTEGER NOT NULL,
    shots INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'SENT',
    status_at INTEGER NOT NULL,
    number TEXT,
    fixed_in TEXT,
    explanation TEXT,
    UNIQUE (user_id, client_id)
);
CREATE INDEX bug_reports_user ON bug_reports(user_id);
CREATE TABLE bug_report_history (
    report_id TEXT NOT NULL REFERENCES bug_reports(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    at INTEGER NOT NULL,
    version TEXT
);
CREATE INDEX bug_report_history_report ON bug_report_history(report_id);
-- B-007: the refresh token before the last renewal still works for a short while (a lost answer must not sign out).
ALTER TABLE sessions ADD COLUMN previous_token_hash TEXT;
ALTER TABLE sessions ADD COLUMN renewed_at INTEGER;

-- Stage 4: the timetable comes straight from the school diary (eduVulcan) instead of vulcan_digest.

-- A parent account of a diary connected to a community; credential = the diary's key, encrypted by the server.
CREATE TABLE diary_connections (
    id              TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    kind            TEXT NOT NULL,
    added_by        TEXT NOT NULL,
    credential      TEXT NOT NULL,
    created_at      INTEGER NOT NULL,
    last_sync_at    INTEGER,
    last_error      TEXT
);

-- Which student of the community a pupil of the diary is (pupil_key: the diary's own id of the pupil).
CREATE TABLE diary_pupils (
    connection_id TEXT NOT NULL REFERENCES diary_connections(id) ON DELETE CASCADE,
    pupil_key     TEXT NOT NULL,
    student_id    TEXT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    school        TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (connection_id, pupil_key)
);

-- A change from the diary: CANCELLED / SUBSTITUTION / CHANGED and what the diary says about it.
ALTER TABLE lessons ADD COLUMN change_kind TEXT;
ALTER TABLE lessons ADD COLUMN change_note TEXT NOT NULL DEFAULT '';

-- vulcan_digest's import tokens are gone with the import.
DROP TABLE integration_tokens;

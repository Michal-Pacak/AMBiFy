-- Stage 4 (v0.6): the community's own roles, the Owner's permission choices, requests waiting for approval,
-- no more minors' profiles (children are members with their own accounts), and the timetable.

-- Roles of each community: the Owner's role is fixed; the others are the community's own. Existing communities
-- get Owner, Admin and User, and memberships point at the role's id instead of the old fixed names.
CREATE TABLE community_roles (
    id              TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    rank            INTEGER NOT NULL,
    is_owner        INTEGER NOT NULL DEFAULT 0,
    created_at      INTEGER NOT NULL
);

INSERT INTO community_roles (id, organization_id, name, rank, is_owner, created_at)
    SELECT id || ':owner', id, 'Owner', 1000000, 1, created_at FROM organizations;

INSERT INTO community_roles (id, organization_id, name, rank, is_owner, created_at)
    SELECT id || ':admin', id, 'Admin', 200, 0, created_at FROM organizations;

INSERT INTO community_roles (id, organization_id, name, rank, is_owner, created_at)
    SELECT id || ':user', id, 'User', 100, 0, created_at FROM organizations;

UPDATE memberships SET role = organization_id || ':' ||
    CASE role WHEN 'OWNER' THEN 'owner' WHEN 'ADMIN' THEN 'admin' ELSE 'user' END;

-- Only the cells the Owner changed; every other cell is the catalog's default for the role's place.
CREATE TABLE role_permissions (
    role_id TEXT NOT NULL REFERENCES community_roles(id) ON DELETE CASCADE,
    action  TEXT NOT NULL,
    mode    TEXT NOT NULL,
    PRIMARY KEY (role_id, action)
);

-- Actions done "after approval": what was asked (payload, JSON) waits here for an Admin or the Owner.
CREATE TABLE permission_requests (
    id              TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    requester_id    TEXT NOT NULL,
    action          TEXT NOT NULL,
    payload         TEXT NOT NULL,
    summary         TEXT NOT NULL,
    status          TEXT NOT NULL,
    created_at      INTEGER NOT NULL,
    decided_by      TEXT,
    decided_at      INTEGER
);

DROP TABLE child_profiles;

-- Timetable: students come from Vulcan (via vulcan_digest), optionally linked to a member's account.
CREATE TABLE students (
    id              TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    clazz           TEXT NOT NULL,
    member_id       TEXT,
    created_at      INTEGER NOT NULL,
    UNIQUE (organization_id, name)
);

CREATE TABLE lessons (
    student_id TEXT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    date       TEXT NOT NULL,
    position   INTEGER NOT NULL,
    subject    TEXT NOT NULL,
    room       TEXT NOT NULL,
    teacher    TEXT NOT NULL,
    time       TEXT NOT NULL,
    PRIMARY KEY (student_id, date, position)
);

-- recurrence and calendars are JSON (the shared Recurrence and a list of calendar names).
CREATE TABLE extra_activities (
    id              TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    student_id      TEXT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    place           TEXT,
    start_date      TEXT NOT NULL,
    start_time      TEXT NOT NULL,
    end_time        TEXT NOT NULL,
    recurrence      TEXT NOT NULL,
    add_to_calendar INTEGER NOT NULL DEFAULT 0,
    calendars       TEXT NOT NULL,
    created_by      TEXT NOT NULL,
    created_at      INTEGER NOT NULL
);

CREATE TABLE activity_reminders (
    activity_id    TEXT NOT NULL REFERENCES extra_activities(id) ON DELETE CASCADE,
    member_id      TEXT NOT NULL,
    kind           TEXT NOT NULL,
    minutes_before INTEGER NOT NULL,
    PRIMARY KEY (activity_id, member_id, kind, minutes_before)
);

-- vulcan_digest proves which community it writes to with this token (stored hashed).
CREATE TABLE integration_tokens (
    token_hash      TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    kind            TEXT NOT NULL,
    created_at      INTEGER NOT NULL
);

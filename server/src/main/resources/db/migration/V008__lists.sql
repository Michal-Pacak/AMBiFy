-- Stage 5 (v0.7): Lists. Phones keep their own copy and ask for what changed since the last "rev" they saw:
-- every change in a community takes the next number of its counter (list_revs) and stores it on the changed row.
-- Deleted lists and items stay as rows with deleted = 1, so phones learn about the deletion.

CREATE TABLE list_revs (
    organization_id TEXT PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    rev             INTEGER NOT NULL
);

CREATE TABLE lists (
    id              TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    type            TEXT NOT NULL,
    name            TEXT NOT NULL,
    everyone        INTEGER NOT NULL DEFAULT 1,
    created_by      TEXT NOT NULL,
    created_at      INTEGER NOT NULL,
    archived        INTEGER NOT NULL DEFAULT 0,
    deleted         INTEGER NOT NULL DEFAULT 0,
    rev             INTEGER NOT NULL
);
CREATE INDEX lists_rev ON lists (organization_id, rev);

-- People of a list that is not for everyone (the creator is always one of them).
CREATE TABLE list_members (
    list_id TEXT NOT NULL REFERENCES lists(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    PRIMARY KEY (list_id, user_id)
);

-- values: JSON (shared ItemValues). field_revs: JSON {field: {rev, by}} — who changed each field last and in
-- which rev, to tell a conflict (someone else changed the same field after the phone last saw the item).
CREATE TABLE list_items (
    id              TEXT PRIMARY KEY,
    list_id         TEXT NOT NULL REFERENCES lists(id) ON DELETE CASCADE,
    organization_id TEXT NOT NULL,
    item_values     TEXT NOT NULL,
    field_revs      TEXT NOT NULL,
    checked         INTEGER NOT NULL DEFAULT 0,
    checked_by      TEXT,
    checked_at      INTEGER,
    added_by        TEXT NOT NULL,
    added_at        INTEGER NOT NULL,
    changed_by      TEXT,
    changed_at      INTEGER,
    cleared         INTEGER NOT NULL DEFAULT 0,
    pending         INTEGER NOT NULL DEFAULT 0,
    last_done_by    TEXT,
    last_done_at    INTEGER,
    deleted         INTEGER NOT NULL DEFAULT 0,
    rev             INTEGER NOT NULL
);
CREATE INDEX list_items_rev ON list_items (organization_id, rev);
CREATE INDEX list_items_list ON list_items (list_id);

-- versions: JSON (list of shared ConflictVersion).
CREATE TABLE list_conflicts (
    id              TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL,
    list_id         TEXT NOT NULL REFERENCES lists(id) ON DELETE CASCADE,
    item_id         TEXT NOT NULL,
    field           TEXT NOT NULL,
    versions        TEXT NOT NULL,
    created_at      INTEGER NOT NULL,
    resolved        INTEGER NOT NULL DEFAULT 0,
    resolved_by     TEXT,
    resolved_at     INTEGER,
    resolution      TEXT,
    rev             INTEGER NOT NULL
);
CREATE INDEX list_conflicts_rev ON list_conflicts (organization_id, rev);

-- The community's own units (on top of the built-in ones); forms: JSON list (one / two-four / five).
CREATE TABLE list_units (
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    forms           TEXT NOT NULL,
    step            REAL NOT NULL,
    pack_unit       TEXT,
    rev             INTEGER NOT NULL,
    PRIMARY KEY (organization_id, name)
);

-- The unit remembered per product, shared by the whole community on every shopping list.
CREATE TABLE list_products (
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    product         TEXT NOT NULL,
    unit            TEXT NOT NULL,
    pack_size       TEXT,
    rev             INTEGER NOT NULL,
    PRIMARY KEY (organization_id, product)
);

-- Changes already done, by the phone's change id: a change sent again (the answer got lost) is not done twice.
CREATE TABLE list_changes_done (
    id         TEXT PRIMARY KEY,
    user_id    TEXT NOT NULL,
    ok         INTEGER NOT NULL,
    message    TEXT,
    queued     INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

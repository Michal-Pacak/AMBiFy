-- 0.7.1: the community's memory of things added to lists ("Z pamięci"). One row per kind of list (scope: SHOPPING,
-- PACKING, CUSTOM, TASKS, DEADLINES) and product (product_key: name + kind, lower case). item_values: the last
-- parameters (JSON ItemValues without dates). Forgotten products stay as rows with deleted = 1, so phones learn it.
-- The rows are built from the items already on the lists the first time a community syncs after this migration.

CREATE TABLE list_memory (
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    scope           TEXT NOT NULL,
    product_key     TEXT NOT NULL,
    item_values     TEXT NOT NULL,
    use_count       INTEGER NOT NULL DEFAULT 1,
    last_used_at    INTEGER NOT NULL,
    last_by         TEXT,
    deleted         INTEGER NOT NULL DEFAULT 0,
    rev             INTEGER NOT NULL,
    PRIMARY KEY (organization_id, scope, product_key)
);

-- 0.7 after the first test of Lists: how a list of things works (style: JSON ListStyle), who may do what on a list
-- (access: JSON list of ListAccess; empty = the old "everyone / members"), the history of done things (cleared_at,
-- cleared_by), sub-items (parent_id, used from 0.7.1) and kinds of products used before ("Mleko 3,2%").

ALTER TABLE lists ADD COLUMN style TEXT;
ALTER TABLE lists ADD COLUMN access TEXT NOT NULL DEFAULT '[]';

ALTER TABLE list_items ADD COLUMN cleared_at INTEGER;
ALTER TABLE list_items ADD COLUMN cleared_by TEXT;
ALTER TABLE list_items ADD COLUMN parent_id TEXT;

CREATE TABLE list_variants (
    organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    product         TEXT NOT NULL,
    variant         TEXT NOT NULL,
    used_at         INTEGER NOT NULL,
    rev             INTEGER NOT NULL,
    PRIMARY KEY (organization_id, product, variant)
);

-- 0.7.3: photos and files of list items (premium LISTS_ATTACHMENTS); the bytes live in lists-files/ next to the database.
CREATE TABLE list_files (
    id              TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL,
    list_id         TEXT NOT NULL,
    item_id         TEXT NOT NULL,
    name            TEXT NOT NULL,
    mime            TEXT NOT NULL,
    size            INTEGER NOT NULL,
    photo           INTEGER NOT NULL,
    added_by        TEXT NOT NULL,
    added_at        INTEGER NOT NULL,
    deleted         INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX list_files_item ON list_files (item_id);
CREATE INDEX list_files_org ON list_files (organization_id, deleted);

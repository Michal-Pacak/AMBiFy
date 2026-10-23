-- Basic server metadata; real tables (accounts, organizations) come in Stage 2.
CREATE TABLE server_info (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT INTO server_info(key, value) VALUES ('created_at', datetime('now'));

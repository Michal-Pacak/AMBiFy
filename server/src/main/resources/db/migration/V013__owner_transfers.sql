-- 0.7.5: the Owner hands the community over (board 16 of the Dashboard canvas). One open request per community;
-- the chosen person accepts or declines; after 7 days it expires. [leave] = the Owner leaves once it is accepted,
-- else they stay with the highest other role.

CREATE TABLE owner_transfers (
    organization_id TEXT PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    from_user       TEXT NOT NULL,
    to_user         TEXT NOT NULL,
    leave_after     INTEGER NOT NULL,
    created_at      INTEGER NOT NULL,
    expires_at      INTEGER NOT NULL
);

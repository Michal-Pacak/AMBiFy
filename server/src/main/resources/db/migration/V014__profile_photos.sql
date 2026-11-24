-- 0.7.6: profile photos. The photo belongs to the account (the same in every community); a community may switch
-- photos off, and someone allowed to may hide one person's photo there.
ALTER TABLE users ADD COLUMN photo_id TEXT;
ALTER TABLE organizations ADD COLUMN photos_blocked INTEGER NOT NULL DEFAULT 0;
ALTER TABLE memberships ADD COLUMN photo_hidden_by TEXT;
ALTER TABLE memberships ADD COLUMN photo_hidden_at INTEGER;

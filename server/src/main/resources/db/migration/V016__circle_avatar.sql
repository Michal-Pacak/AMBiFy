-- 0.7.8: a circle's (community's) own icon — a color for its initials or a photo, set by the Owner.
ALTER TABLE organizations ADD COLUMN avatar_color TEXT;
ALTER TABLE organizations ADD COLUMN avatar_photo_id TEXT;
-- The rounded square is the circles' shape now: people who had it get the first free shape again.
UPDATE memberships SET avatar_shape = NULL WHERE avatar_shape = 'SQUARE';

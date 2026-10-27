-- v0.5.1: a person's icon in each community (initials come from the name). Shape and color are handed out once,
-- when someone joins or a minor is added, and never change when others join. Rows from before this migration
-- get theirs on the first read of their community (in joining order).
ALTER TABLE memberships ADD COLUMN avatar_shape TEXT;

ALTER TABLE memberships ADD COLUMN avatar_color TEXT;

-- Minors keep the color chosen for them; only the shape is handed out.
ALTER TABLE child_profiles ADD COLUMN avatar_shape TEXT;

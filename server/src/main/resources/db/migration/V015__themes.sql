-- 0.7.7: themes (canvas "Motywy"). The account's theme (screens outside a community) and Premium konta; each
-- member's own pick in a community; the community's theme set by the Owner, possibly locked for everyone.
ALTER TABLE users ADD COLUMN theme TEXT;
ALTER TABLE users ADD COLUMN account_premium INTEGER NOT NULL DEFAULT 0;
ALTER TABLE memberships ADD COLUMN theme TEXT;
ALTER TABLE organizations ADD COLUMN theme TEXT;
ALTER TABLE organizations ADD COLUMN theme_locked INTEGER NOT NULL DEFAULT 0;
ALTER TABLE organizations ADD COLUMN theme_set_by TEXT;

-- Themes made in the editor: an account's (user_id) or a community's (organization_id); spec = OwnTheme as JSON.
CREATE TABLE own_themes (
    id TEXT PRIMARY KEY,
    user_id TEXT REFERENCES users(id) ON DELETE CASCADE,
    organization_id TEXT REFERENCES organizations(id) ON DELETE CASCADE,
    spec TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX own_themes_user ON own_themes(user_id);
CREATE INDEX own_themes_org ON own_themes(organization_id);

-- Wallpaper photos (files in wallpapers/<id>.jpg next to the database), of an account or of a community.
CREATE TABLE wallpapers (
    id TEXT PRIMARY KEY,
    user_id TEXT REFERENCES users(id) ON DELETE CASCADE,
    organization_id TEXT REFERENCES organizations(id) ON DELETE CASCADE,
    created_at INTEGER NOT NULL
);

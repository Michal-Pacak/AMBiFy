-- Each person's own Dashboard in each community: the widgets and their places, as JSON (shared Dashboard).
ALTER TABLE memberships ADD COLUMN dashboard TEXT;

-- 0.7.2: "Inny dostęp do tego zadania" of an item with sub-items (access: JSON list of ListAccess; empty = as on
-- the list; sub-items use the one of their item, parent_id comes from V009). Units nobody gave forms to so far
-- were saved as the name three times: now empty forms, which the phones guess from the name ("wiaderko" → "3 wiaderka").

ALTER TABLE list_items ADD COLUMN access TEXT NOT NULL DEFAULT '[]';

UPDATE list_units SET forms = '[]' WHERE forms = json_array(name, name, name);

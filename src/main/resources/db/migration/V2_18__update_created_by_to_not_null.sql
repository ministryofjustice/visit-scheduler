UPDATE visit SET created_by = 'NOT_KNOWN' where created_by is NULL;
ALTER TABLE visit ALTER COLUMN created_by SET NOT NULL;
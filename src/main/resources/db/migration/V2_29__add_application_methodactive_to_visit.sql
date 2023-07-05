ALTER TABLE visit ADD last_application_method varchar(90);
UPDATE visit SET last_application_method = 'NOT_KNOWN';
ALTER TABLE visit ALTER COLUMN last_application_method SET NOT NULL

ALTER TABLE visit ADD last_application_method varchar(90);
UPDATE visit SET last_application_method = 'UN_KNOWN';
ALTER TABLE visit ALTER COLUMN last_application_method SET NOT NULL

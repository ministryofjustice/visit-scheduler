ALTER TABLE visit
    ADD COLUMN visit_restriction   VARCHAR(80)
;

UPDATE visit SET visit_restriction = 'OPEN';

ALTER TABLE visit ALTER COLUMN visit_restriction SET NOT NULL;
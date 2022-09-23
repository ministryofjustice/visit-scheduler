ALTER TABLE visit DROP CONSTRAINT visit_reference_key;
ALTER TABLE visit ADD application_reference VARCHAR(40) UNIQUE;
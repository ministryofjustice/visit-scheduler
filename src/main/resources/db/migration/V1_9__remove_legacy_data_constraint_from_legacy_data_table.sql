ALTER TABLE legacy_data DROP CONSTRAINT legacy_data_visit_id_lead_person_id_key;
-- In sybase you must drop a column and then recreate it to remove NOT NULL
ALTER TABLE legacy_data DROP lead_person_id;
ALTER TABLE legacy_data ADD COLUMN lead_person_id integer;
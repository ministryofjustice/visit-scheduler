BEGIN;

ALTER TABLE session_template ADD COLUMN is_age_restricted boolean DEFAULT FALSE;
UPDATE session_template SET is_age_restricted = false;
ALTER TABLE session_template ALTER COLUMN is_age_restricted SET NOT NULL;

ALTER TABLE session_template ADD COLUMN age_restriction integer DEFAULT 18;
UPDATE session_template SET age_restriction = 18;
ALTER TABLE session_template ALTER COLUMN age_restriction SET NOT NULL;

COMMIT;
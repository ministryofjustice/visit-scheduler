ALTER TABLE session_template ADD enhanced boolean DEFAULT FALSE;
UPDATE session_template SET enhanced = false;
ALTER TABLE session_template ALTER COLUMN enhanced SET NOT NULL;
ALTER TABLE session_template ADD COLUMN adult_only BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE session_template ADD COLUMN adult_age_threshold INT NOT NULL DEFAULT 18;

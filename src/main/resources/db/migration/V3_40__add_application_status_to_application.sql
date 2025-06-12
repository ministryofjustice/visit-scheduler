-- add application_status column to application  and set DEFAULT to ACCEPTED for timebeing
ALTER TABLE application ADD application_status VARCHAR(80) NOT NULL DEFAULT 'ACCEPTED';
-- update all applications that should not be ACCEPTED to IN_PROGRESS
UPDATE application set application_status = 'IN_PROGRESS' where (completed IS NULL OR completed = false);
-- finally drop the default value of ACCEPTED from application table
ALTER TABLE application ALTER COLUMN application_status DROP DEFAULT;

-- add application_status column to application
ALTER TABLE application ADD application_status VARCHAR(80);
UPDATE application set application_status = 'IN_PROGRESS' where (completed IS NULL OR completed = false);
UPDATE application set application_status = 'ACCEPTED' where completed = true;
ALTER TABLE application ALTER COLUMN application_status SET NOT NULL;
-- Add indexes at end other wise updates will be slowed down.
ALTER TABLE application ADD CONSTRAINT application_pk PRIMARY KEY (id);
-- may be change to serial4
CREATE INDEX idx_application_reference ON application(reference);


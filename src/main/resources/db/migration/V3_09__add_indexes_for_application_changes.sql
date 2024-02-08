CREATE INDEX idx_visit_prison        ON visit(prison_id);
CREATE INDEX idx_reference_for_visit ON visit(reference);

CREATE INDEX idx_application_visit_id ON application(visit_id);

-- update serial sequences
SELECT setval(pg_get_serial_sequence('visit', 'id'), max(id)) FROM visit;
SELECT setval(pg_get_serial_sequence('application', 'id'), max(id)) FROM application;
SELECT setval(pg_get_serial_sequence('application_contact', 'id'), max(id)) FROM application_contact;
SELECT setval(pg_get_serial_sequence('application_support', 'id'), max(id)) FROM application_support;
SELECT setval(pg_get_serial_sequence('application_visitor', 'id'), max(id)) FROM application_visitor;

-- These foreign keys exist on some DB's instance but they belong to the backup table so delete
ALTER TABLE visit_contact DROP CONSTRAINT IF EXISTS fk_contact_to_visit;
ALTER TABLE visit_notes DROP CONSTRAINT IF EXISTS fk_notes_to_visit;
ALTER TABLE visit_support DROP CONSTRAINT IF EXISTS fk_support_to_visit;
ALTER TABLE visit_visitor DROP CONSTRAINT IF EXISTS fk_visitor_to_visit;

-- THESE WERE PREVENTING THE SYSTEM FROM DELETING THE CHILD OBJECTS
-- Now re-create foreign keys tables
-- ALTER TABLE visit_contact  ADD CONSTRAINT fk_contact_to_visit FOREIGN KEY (visit_id) REFERENCES visit(id);
-- ALTER TABLE visit_notes  ADD CONSTRAINT fk_notes_to_visit FOREIGN KEY (visit_id) REFERENCES visit(id);
-- ALTER TABLE visit_support  ADD CONSTRAINT fk_support_to_visit FOREIGN KEY (visit_id) REFERENCES visit(id);
-- ALTER TABLE visit_visitor  ADD CONSTRAINT fk_visitor_to_visit FOREIGN KEY (visit_id) REFERENCES visit(id);




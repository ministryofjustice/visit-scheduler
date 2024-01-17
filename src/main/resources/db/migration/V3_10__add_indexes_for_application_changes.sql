CREATE INDEX idx_visit_prison        ON visit(prison_id);
CREATE INDEX idx_reference_for_visit ON visit(reference);

-- update serial sequences
SELECT setval(pg_get_serial_sequence('visit', 'id'), max(id)) FROM visit;
SELECT setval(pg_get_serial_sequence('application', 'id'), max(id)) FROM application;
SELECT setval(pg_get_serial_sequence('application_contact', 'id'), max(id)) FROM application_contact;
SELECT setval(pg_get_serial_sequence('application_support', 'id'), max(id)) FROM application_support;
SELECT setval(pg_get_serial_sequence('application_visitor', 'id'), max(id)) FROM application_visitor;



INSERT INTO application_visitor
    SELECT id,
           visit_id,
           nomis_person_id,
           visit_contact FROM visit_visitor order by id;

CREATE INDEX idx_application_visitor_to_application_id ON application_visitor(application_id);



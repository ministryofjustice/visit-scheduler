
INSERT INTO application_visitor
    SELECT id,
           visit_id,
           nomis_person_id,
           visit_contact FROM visit_visitor order by id;



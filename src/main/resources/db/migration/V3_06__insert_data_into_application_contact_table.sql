
INSERT INTO application_contact
    SELECT id,
           visit_id,
           contact_name,
           contact_phone FROM visit_contact order by id;

CREATE INDEX idx_application_contact_to_application_id ON application_contact(application_id);



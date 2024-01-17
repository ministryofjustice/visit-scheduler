
INSERT INTO application_contact
    SELECT id,
           visit_id,
           contact_name,
           contact_phone FROM visit_contact order by id;


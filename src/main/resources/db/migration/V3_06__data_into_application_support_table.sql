INSERT INTO application_support
    SELECT id,
           visit_id,
           type,
           text FROM visit_support order by id;

CREATE INDEX idx_application_support_to_application_id ON application_support(application_id);



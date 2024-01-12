INSERT INTO application_support
    SELECT id,
           visit_id,
           type,
           text FROM visit_support;


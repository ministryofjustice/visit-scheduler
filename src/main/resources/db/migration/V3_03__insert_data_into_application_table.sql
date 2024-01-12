INSERT INTO application
SELECT
    id,
    prison_id,
    prisoner_id,
    session_slot_id,
    application_reference,
    reference,
    visit_type,
    visit_restriction,
    created_by,
    create_timestamp,
    modify_timestamp
FROM  tmp_visit v order by id;


-- Drop session info
ALTER TABLE application DROP slot_date;
ALTER TABLE application DROP slot_time;
ALTER TABLE application DROP slot_end_time;
ALTER TABLE application DROP session_template_reference;




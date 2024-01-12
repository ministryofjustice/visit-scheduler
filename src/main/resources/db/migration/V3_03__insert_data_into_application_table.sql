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




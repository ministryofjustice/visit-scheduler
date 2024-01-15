INSERT INTO application
    SELECT
        id,
        prison_id,
        prisoner_id,
        session_slot_id,
        visit_status = 'RESERVED' as reserved_slot,
        application_reference,
        reference,
        visit_type,
        visit_restriction,
        created_by,
        create_timestamp,
        modify_timestamp
    FROM  tmp_visit v;


ALTER TABLE tmp_visit DROP application_reference;


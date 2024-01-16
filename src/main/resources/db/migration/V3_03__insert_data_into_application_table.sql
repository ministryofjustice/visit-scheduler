INSERT INTO application (   id,
                            prison_id,
                            prisoner_id,
                            session_slot_id,
                            reserved_slot,
                            reference,
                            booking_reference,
                            visit_type,
                            restriction,
                            completed,
                            created_by,
                            create_timestamp,
                            modify_timestamp)
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
        visit_status not in ('RESERVED','CHANGING') as completed,
        created_by,
        create_timestamp,
        modify_timestamp
    FROM  tmp_visit v order by id;


ALTER TABLE tmp_visit DROP application_reference;


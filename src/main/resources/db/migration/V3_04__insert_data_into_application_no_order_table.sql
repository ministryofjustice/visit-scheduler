CREATE TEMP TABLE tmp_not_reserved_applications(application_reference text UNIQUE NOT NULL);

-- create temp table to store not reserved applications
INSERT INTO tmp_not_reserved_applications(application_reference)
   SELECT application_reference FROM event_audit ea WHERE ea."type" = 'CHANGING_VISIT';

CREATE TABLE tmp_application AS TABLE application WITH NO DATA;

-- Insert data from visit table to tmp application table
INSERT INTO tmp_application (   id,
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
        nr.application_reference is null as reserved_slot,
        v.application_reference,
        reference,
        visit_type,
        visit_restriction,
        visit_status not in ('RESERVED','CHANGING') as completed,
        created_by,
        create_timestamp,
        modify_timestamp
    FROM  tmp_visit v
        LEFT JOIN tmp_not_reserved_applications nr on nr.application_reference = v.application_reference
    order by id;

DROP TABLE tmp_not_reserved_applications;
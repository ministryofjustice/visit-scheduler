
-- insert tmp application data into application in order aslo add visit id
INSERT INTO application (id,
                        prison_id,
                        prisoner_id,
                        session_slot_id,
                        reserved_slot,
                        reference,
                        booking_reference,
                        visit_type,
                        restriction,
                        completed,
                        visit_id,
                        created_by,
                        create_timestamp,
                        modify_timestamp)
    SELECT  a.id,
            a.prison_id,
            a.prisoner_id,
            a.session_slot_id,
            a.reserved_slot,
            a.reference,
            a.booking_reference,
            a.visit_type,
            a.restriction,
            a.completed,
            v.id,
            a.created_by,
            a.create_timestamp,
            a.modify_timestamp
        FROM tmp_application a
            JOIN visit v on v.reference = a.booking_reference
        WHERE v.visit_status  = 'BOOKED' or (v.visit_status  = 'CANCELLED' and (v.outcome_status is null or v.outcome_status != 'SUPERSEDED_CANCELLATION'))
        Order by a.id;

-- Drop columns that are no longer needed
ALTER TABLE tmp_visit   DROP application_reference;
ALTER TABLE application DROP booking_reference;

TRUNCATE TABLE tmp_application;
DROP TABLE tmp_application;


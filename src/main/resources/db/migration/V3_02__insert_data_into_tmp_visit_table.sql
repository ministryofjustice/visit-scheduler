CREATE TABLE tmp_visit_no_order AS TABLE tmp_visit WITH NO DATA;


INSERT INTO tmp_visit_no_order
SELECT  id,
        prison_id,
        prisoner_id,
        new_session_slot_id,
        reference,
        application_reference,
        visit_type,
        visit_room,
        visit_status,
        visit_restriction,
        outcome_status,
        created_by,
        updated_by,
        cancelled_by,
        create_timestamp,
        modify_timestamp
FROM   (SELECT V.*,ss.id AS new_session_slot_id FROM visit v
                        JOIN session_slot ss on  (
                             ss.session_template_reference IS NULL AND
                             ss.prison_id 		= v.prison_id AND
                             ss.slot_time 		= visit_start::TIME AND
                             ss.slot_end_time  	= visit_end::TIME
    ) WHERE  v.session_template_reference IS null) tmp;


INSERT INTO tmp_visit_no_order
SELECT  id,
        prison_id,
        prisoner_id,
        new_session_slot_id,
        reference,
        application_reference,
        visit_type,
        visit_room,
        visit_status,
        visit_restriction,
        outcome_status,
        created_by,
        updated_by,
        cancelled_by,
        create_timestamp,
        modify_timestamp
FROM   (

           SELECT V.*,ss.id  AS new_session_slot_id FROM visit v
                                                             JOIN session_slot ss ON  (
                       ss.session_template_reference = v.session_template_reference AND
                       ss.prison_id 		= v.prison_id AND
                       ss.slot_date 		= visit_start::DATE
               )) tmp;


INSERT INTO tmp_visit SELECT * FROM tmp_visit_no_order order by id;
TRUNCATE TABLE tmp_visit_no_order;
DROP TABLE tmp_visit_no_order;

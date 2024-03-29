-- Inserts visits into tmp_visit_no_order table, with no session templates

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
                             ss.slot_date 		= visit_start::DATE AND
                             ss.slot_start 	    = v.visit_start AND
                             ss.slot_end  	    = v.visit_end
    ) WHERE  v.session_template_reference IS null) tmp;
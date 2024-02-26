-- Inserts visits into tmp_visit_no_order table, with session templates

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
               ) WHERE  v.session_template_reference IS NOT null
        ) tmp;


-- We now need to order visits as they were inserted by two separate queries
INSERT INTO tmp_visit SELECT * FROM tmp_visit_no_order order by id;

TRUNCATE TABLE tmp_visit_no_order;
DROP TABLE tmp_visit_no_order;

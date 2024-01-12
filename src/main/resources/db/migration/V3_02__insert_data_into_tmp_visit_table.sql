insert into tmp_visit
select	id,
          prison_id,
          prisoner_id,
          new_session_slot_id,
          reference,
          application_reference,
          session_template_reference,
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
           SELECT V.*,ss.id AS new_session_slot_id FROM visit v
                JOIN session_slot ss on  (ss.session_template_reference is null and
                                     ss.prison_id 		= v.prison_id and
                                     ss.slot_date 		= visit_start::date and
                                     ss.slot_time 		= visit_start::time and
                                     ss.slot_end_time  	= visit_end::time
               ) WHERE  v.session_template_reference is null
           UNION
           SELECT V.*,ss.id  AS new_session_slot_id FROM visit v
                JOIN session_slot ss on  (
                                       ss.session_template_reference = v.session_template_reference and
                                       ss.prison_id 		= v.prison_id and
                                       ss.slot_date 		= visit_start::date
               )) tmp order by id;


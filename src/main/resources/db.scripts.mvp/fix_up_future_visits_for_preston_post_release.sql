BEGIN;
--update visits that have an incorrect end time of '14:59:00' instead of '15:00:00'
--matching session timetable value is 14:00:00	15:00:00

update visit set visit_end = visit_end + interval '1 minutes'
where prison_id  in (
    select prison_id from prison p where p.code = 'PNI'
)
  and 	visit_start >= TO_DATE('02/02/2023', 'DD/MM/YYYY')
  and (visit_start::time = '14:00:00')
  and (visit_end::time = '14:59:00')
  and visit_status = 'BOOKED';


--update visits that have an incorrect end time of '16:29:00' instead of '16:30:00'
--matching session timetable value is 15:30:00	16:30:00

update visit set visit_end = visit_end + interval '1 minutes'
where prison_id  in (
    select prison_id from prison p where p.code = 'PNI'
)
  and 	visit_start >= TO_DATE('02/02/2023', 'DD/MM/YYYY')
  and (visit_start::time = '15:30:00')
  and (visit_end::time = '16:29:00')
  and visit_status = 'BOOKED';

END;

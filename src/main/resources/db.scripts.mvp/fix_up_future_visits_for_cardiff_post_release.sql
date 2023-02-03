BEGIN;
--update visits that have an incorrect end time of '19:00:00' instead of '18:45:00'
--matching session timetable value is 18:00:00	18:45:00 on TUESDAY

update visit set visit_end = visit_end - interval '15 minutes'
where prison_id  in (
    select prison_id from prison p where p.code = 'CFI'
)
  and 	visit_start >= TO_DATE('02/02/2023', 'DD/MM/YYYY')
  and visit_start::time = '18:00:00'
  and visit_end::time = '19:00:00'
  and visit_status = 'BOOKED'
  and UPPER(TRIM(To_Char(visit_start , 'DAY'))) = 'TUESDAY';

--update visits that have an incorrect end time of '15:15:00' instead of '15:45:00'
--matching session timetable value is 13:45:00 to 15:45:00 on all days

update visit set visit_end = visit_end + interval '30 minutes'
where prison_id  in (
    select prison_id from prison p where p.code = 'CFI'
)
  and 	visit_start >= TO_DATE('02/02/2023', 'DD/MM/YYYY')
  and visit_start::time = '13:45:00'
  and visit_end::time = '15:15:00'
  and visit_status = 'BOOKED';

END;

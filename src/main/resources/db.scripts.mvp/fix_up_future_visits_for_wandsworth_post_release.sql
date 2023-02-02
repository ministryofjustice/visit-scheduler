BEGIN;
--update visits that have an incorrect start time of '15:15:00' and end time of '16:15:00' instead of start time of '15:30:00' and end time of '16:30:00'
--matching session timetable value is 15:30:00	16:30:00 on Saturday and Sunday

update visit set visit_start = visit_start + interval '15 minutes',
visit_end = visit_end + interval '15 minutes'
where prison_id  in (
    select prison_id from prison p where p.code = 'WWI'
)
  and 	visit_start >= TO_DATE('02/02/2023', 'DD/MM/YYYY')
  and (visit_start::time = '15:15:00')
  and (visit_end::time = '16:15:00')
  and visit_status = 'BOOKED'
  and UPPER(TRIM(To_Char(visit_start , 'DAY'))) in ('SATURDAY', 'SUNDAY');

END;

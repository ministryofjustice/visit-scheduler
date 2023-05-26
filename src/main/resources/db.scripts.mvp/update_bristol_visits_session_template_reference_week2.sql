BEGIN;

SET SCHEMA 'public';

--update TUESDAY - week 2 - 14:00 to 15:00 visits
update visit set session_template_reference = (
    select st.reference from session_template st
    where prison_id = (select id from prison where code = 'BLI')
      and st.valid_to_date IS NULL
      and st.day_of_week = 'TUESDAY'
      and st.start_time = '14:00:00'
      and st.end_time  = '15:00:00'
      and st.valid_from_date = '2023-05-29')
where prison_id = (select id from prison where code = 'BLI')
  and BTRIM(To_Char(visit_start , 'DAY')) = 'TUESDAY'
  and visit_start::time = '14:00:00'
  and visit_end::time = '15:00:00'
  and visit_start >= current_date
  and session_template_reference =
      (select st.reference from session_template st
       where prison_id = (select id from prison where code = 'BLI')
         and st.valid_to_date = '2023-05-21'
         and st.day_of_week = 'TUESDAY'
         and st.start_time = '14:00:00'
         and st.end_time  = '15:00:00'
         and st.valid_from_date = '2023-01-09');

--update TUESDAY - week 2 - 15:30 to 16:30 visits
update visit set session_template_reference = (
    select st.reference from session_template st
    where prison_id = (select id from prison where code = 'BLI')
      and st.valid_to_date IS NULL
      and st.day_of_week = 'TUESDAY'
      and st.start_time = '15:30:00'
      and st.end_time  = '16:30:00'
      and st.valid_from_date = '2023-05-29')
where prison_id = (select id from prison where code = 'BLI')
  and BTRIM(To_Char(visit_start , 'DAY')) = 'TUESDAY'
  and visit_start::time = '15:30:00'
  and visit_end::time = '16:30:00'
  and visit_start >= current_date
  and session_template_reference =
      (select st.reference from session_template st
       where prison_id = (select id from prison where code = 'BLI')
         and st.valid_to_date = '2023-05-21'
         and st.day_of_week = 'TUESDAY'
         and st.start_time = '15:30:00'
         and st.end_time  = '16:30:00'
         and st.valid_from_date = '2023-01-09');

--update WEDNESDAY - week 2 - 14:00 to 16:00 visits
update visit set session_template_reference = (
    select st.reference from session_template st
    where prison_id = (select id from prison where code = 'BLI')
      and st.valid_to_date IS NULL
      and st.day_of_week = 'WEDNESDAY'
      and st.start_time = '14:00:00'
      and st.end_time  = '16:00:00'
      and st.valid_from_date = '2023-05-29')
where prison_id = (select id from prison where code = 'BLI')
  and BTRIM(To_Char(visit_start , 'DAY')) = 'WEDNESDAY'
  and visit_start::time = '14:00:00'
  and visit_end::time = '16:00:00'
  and visit_start >= current_date
  and session_template_reference =
      (select st.reference from session_template st
       where prison_id = (select id from prison where code = 'BLI')
         and st.valid_to_date = '2023-05-21'
         and st.day_of_week = 'WEDNESDAY'
         and st.start_time = '14:00:00'
         and st.end_time  = '16:00:00'
         and st.valid_from_date = '2023-01-09');

--update FRIDAY - week 2 - 14:00 to 16:00 visits
update visit set session_template_reference = (
    select st.reference from session_template st
    where prison_id = (select id from prison where code = 'BLI')
      and st.valid_to_date IS NULL
      and st.day_of_week = 'FRIDAY'
      and st.start_time = '14:00:00'
      and st.end_time  = '16:00:00'
      and st.valid_from_date = '2023-05-29')
where prison_id = (select id from prison where code = 'BLI')
  and BTRIM(To_Char(visit_start , 'DAY')) = 'FRIDAY'
  and visit_start::time = '14:00:00'
  and visit_end::time = '16:00:00'
  and visit_start >= current_date
  and session_template_reference =
      (select st.reference from session_template st
       where prison_id = (select id from prison where code = 'BLI')
         and st.valid_to_date = '2023-05-21'
         and st.day_of_week = 'FRIDAY'
         and st.start_time = '14:00:00'
         and st.end_time  = '16:00:00'
         and st.valid_from_date = '2023-01-09');

--update SATURDAY - week 2 - 14:00 to 15:00 visits
update visit set session_template_reference = (
    select st.reference from session_template st
    where prison_id = (select id from prison where code = 'BLI')
      and st.valid_to_date IS NULL
      and st.day_of_week = 'SATURDAY'
      and st.start_time = '14:00:00'
      and st.end_time  = '15:00:00'
      and st.valid_from_date = '2023-05-29')
where prison_id = (select id from prison where code = 'BLI')
  and BTRIM(To_Char(visit_start , 'DAY')) = 'SATURDAY'
  and visit_start::time = '14:00:00'
  and visit_end::time = '15:00:00'
  and visit_start >= current_date
  and session_template_reference =
      (select st.reference from session_template st
       where prison_id = (select id from prison where code = 'BLI')
         and st.valid_to_date = '2023-05-21'
         and st.day_of_week = 'SATURDAY'
         and st.start_time = '14:00:00'
         and st.end_time  = '15:00:00'
         and st.valid_from_date = '2023-01-09');

--update SATURDAY - week 2 - 15:30 to 16:30 visits
update visit set session_template_reference = (
    select st.reference from session_template st
    where prison_id = (select id from prison where code = 'BLI')
      and st.valid_to_date IS NULL
      and st.day_of_week = 'SATURDAY'
      and st.start_time = '15:30:00'
      and st.end_time  = '16:30:00'
      and st.valid_from_date = '2023-05-29')
where prison_id = (select id from prison where code = 'BLI')
  and BTRIM(To_Char(visit_start , 'DAY')) = 'SATURDAY'
  and visit_start::time = '15:30:00'
  and visit_end::time = '16:30:00'
  and visit_start >= current_date
  and session_template_reference =
      (select st.reference from session_template st
       where prison_id = (select id from prison where code = 'BLI')
         and st.valid_to_date = '2023-05-21'
         and st.day_of_week = 'SATURDAY'
         and st.start_time = '15:30:00'
         and st.end_time  = '16:30:00'
         and st.valid_from_date = '2023-01-09');

--update SUNDAY - week 2 - 14:00 to 15:00 visits
update visit set session_template_reference = (
    select st.reference from session_template st
    where prison_id = (select id from prison where code = 'BLI')
      and st.valid_to_date IS NULL
      and st.day_of_week = 'SUNDAY'
      and st.start_time = '14:00:00'
      and st.end_time  = '15:00:00'
      and st.valid_from_date = '2023-05-29')
where prison_id = (select id from prison where code = 'BLI')
  and BTRIM(To_Char(visit_start , 'DAY')) = 'SUNDAY'
  and visit_start::time = '14:00:00'
  and visit_end::time = '15:00:00'
  and visit_start >= current_date
  and session_template_reference =
      (select st.reference from session_template st
       where prison_id = (select id from prison where code = 'BLI')
         and st.valid_to_date = '2023-05-21'
         and st.day_of_week = 'SUNDAY'
         and st.start_time = '14:00:00'
         and st.end_time  = '15:00:00'
         and st.valid_from_date = '2023-01-09');

--update SUNDAY - week 2 - 15:30 to 16:30 visits
update visit set session_template_reference = (
    select st.reference from session_template st
    where prison_id = (select id from prison where code = 'BLI')
      and st.valid_to_date IS NULL
      and st.day_of_week = 'SUNDAY'
      and st.start_time = '15:30:00'
      and st.end_time  = '16:30:00'
      and st.valid_from_date = '2023-05-29')
where prison_id = (select id from prison where code = 'BLI')
  and BTRIM(To_Char(visit_start , 'DAY')) = 'SUNDAY'
  and visit_start::time = '15:30:00'
  and visit_end::time = '16:30:00'
  and visit_start >= current_date
  and session_template_reference =
      (select st.reference from session_template st
       where prison_id = (select id from prison where code = 'BLI')
         and st.valid_to_date = '2023-05-21'
         and st.day_of_week = 'SUNDAY'
         and st.start_time = '15:30:00'
         and st.end_time  = '16:30:00'
         and st.valid_from_date = '2023-01-09');

END;
-- update DURHAM timetable
BEGIN;

SET SCHEMA 'public';

-- this session template is now open to only wing F prisoners
update session_template set visit_room = 'VMH Wing F'
where day_of_week = 'MONDAY'
  AND visit_room = 'VMH Wings C and F'
  AND prison_id = (select id from prison where code = 'DMI');

-- this session template is now open to only wing F prisoners
update session_template set visit_room = 'VMH Wing F'
where day_of_week = 'TUESDAY'
  AND visit_room = 'VMH Wings C and F'
  AND prison_id = (select id from prison where code = 'DMI');

-- this session template is now open to wings C and F prisoners
update session_template set visit_room = 'VMH Wings C and F'
where day_of_week = 'WEDNESDAY'
  AND start_time = '09:30:00'
  AND end_time = '11:30:00'
  AND visit_room = 'Visits Main Hall'
  AND prison_id = (select id from prison where code = 'DMI');

-- this session template is now open to only wing F prisoners
update session_template set visit_room = 'VMH Wing F'
where day_of_week = 'WEDNESDAY'
  AND visit_room = 'VMH Wings C and F'
  AND start_time = '13:45:00'
  AND end_time = '15:45:00'
  AND prison_id = (select id from prison where code = 'DMI');

-- this session template is now open to only wing F prisoners
update session_template set visit_room = 'VMH Wing F'
where day_of_week = 'THURSDAY'
  AND visit_room = 'VMH Wings C and F'
  AND prison_id = (select id from prison where code = 'DMI');

-- this session template is now open to only wing F prisoners
update session_template set visit_room = 'VMH Wing F'
where day_of_week = 'FRIDAY'
  AND visit_room = 'VMH Wings C and F'
  AND prison_id = (select id from prison where code = 'DMI');

-- this session template is now open to only wing F prisoners
update session_template set visit_room = 'VMH Wing F'
where day_of_week = 'SATURDAY'
  AND visit_room = 'VMH Wings C and F'
  AND prison_id = (select id from prison where code = 'DMI');

-- this session template is now open to only wing F prisoners
update session_template set visit_room = 'VMH Wing F'
where day_of_week = 'SUNDAY'
  AND visit_room = 'VMH Wings C and F'
  AND prison_id = (select id from prison where code = 'DMI');

-- remove C from the earlier C and F grouping
delete from permitted_session_location
  where level_one_code = 'C'
  AND group_id = (select id from session_location_group where name = 'Wings C and F'
    AND prison_id = (select id from prison where code = 'DMI'));

-- update session_location_group name to remove C
update session_location_group set name = 'Wing F'
where name = 'Wings C and F'
  AND prison_id = (select id from prison where code = 'DMI');

-- update session_location_group name to add C
update session_location_group set name = 'Wings A, B, C, D, E, G, I, M'
where name = 'Wings A, B, D, E, G, I, M'
  AND prison_id = (select id from prison where code = 'DMI');

-- create a new session_location_group for wings C and F
INSERT INTO session_location_group
(reference, prison_id, "name")
select '-afe~dcc~05', id, 'Wings C and F' from prison where code = 'DMI';

--add wing C to the new Wings C and F grouping
INSERT INTO permitted_session_location
(level_one_code, level_two_code, level_three_code, level_four_code, group_id)
select 'C', NULL, NULL, NULL, id from session_location_group
where reference = '-afe~dcc~05'
  AND name =  'Wings C and F'
  AND prison_id = (select id from prison where code = 'DMI');

--add wing F to the new Wings C and F grouping
INSERT INTO permitted_session_location
(level_one_code, level_two_code, level_three_code, level_four_code, group_id)
select 'F', NULL, NULL, NULL, id from session_location_group
where reference = '-afe~dcc~05'
  AND name =  'Wings C and F'
  AND prison_id = (select id from prison where code = 'DMI');

--add wing C to the existing Wings A, B, D, E, G, I, M grouping
INSERT INTO permitted_session_location
(level_one_code, level_two_code, level_three_code, level_four_code, group_id)
select 'C', NULL, NULL, NULL, id from session_location_group
where  name =  'Wings A, B, C, D, E, G, I, M'
  AND prison_id = (select id from prison where code = 'DMI');

--allocate the WED 09:30 to 11:30 session to the newly created Wings C and F grouping
update session_to_location_group set group_id =
  (select id from session_location_group
    where reference = '-afe~dcc~05'
    AND name =  'Wings C and F'
    AND prison_id = (select id from prison where code = 'DMI'))
    WHERE session_template_id = (
      select id from session_template where visit_room = 'VMH Wings C and F'
        AND day_of_week = 'WEDNESDAY'
        AND start_time = '09:30:00'
        AND end_time = '11:30:00'
        AND prison_id = (select id from prison where code = 'DMI'));

END;

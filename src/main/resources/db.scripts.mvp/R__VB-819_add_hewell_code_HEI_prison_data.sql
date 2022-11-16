-- MVP data for Hewell visit schedule
-- Requirements set out in VB-819
-- Start a transaction
BEGIN;

    SET SCHEMA 'public';

    DELETE FROM session_template WHERE prison_id=(SELECT id FROM prison WHERE code = 'HEI');

    -- start_date MONDAY and then repeat weekly on Monday!
    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id,'Visits Main Room', 'SOCIAL', 30, 2, '13:45:00', '14:45:00', '2022-05-30', null, 'MONDAY'  FROM prison WHERE code = 'HEI';

    -- start_date WEDNESDAY and then repeat weekly on WEDNESDAY!
    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id,'Visits Main Room', 'SOCIAL', 30, 2, '13:45:00', '14:45:00', '2022-06-01', null, 'WEDNESDAY'  FROM prison WHERE code = 'HEI';

    -- start_date FRIDAY then and repeat weekly on FRIDAY!
    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id, 'Visits Main Room', 'SOCIAL', 30, 2, '09:00:00', '10:00:00', '2022-06-03', null, 'FRIDAY'  FROM prison WHERE code = 'HEI';

    -- start_date SATURDAY then and repeat weekly on SATURDAY!
    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id, 'Visits Main Room', 'SOCIAL', 30, 2, '13:45:00', '14:45:00', '2022-06-04', null, 'SATURDAY'  FROM prison WHERE code = 'HEI';

    -- start_date SUNDAY then and repeat weekly on SUNDAY!
    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id, 'Visits Main Room', 'SOCIAL', 30, 2, '13:45:00', '14:45:00', '2022-06-05', null, 'SUNDAY'  FROM prison WHERE code = 'HEI';

-- Commit the change
END;
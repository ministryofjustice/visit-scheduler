-- Data for Bristol visit schedule

-- Start a transaction
BEGIN;

    SET SCHEMA 'public';

    DELETE FROM session_template WHERE prison_id=(SELECT id FROM prison WHERE code = 'BLI');

    -- start_date TUESDAY and then repeat weekly on TUESDAY!
    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '14:00:00', '15:00:00', '2022-11-16', null, 'TUESDAY'  FROM prison WHERE code = 'BLI';

    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '15:30:00', '16:30:00', '2022-11-16', null, 'TUESDAY'  FROM prison WHERE code = 'BLI';

    -- start_date WEDNESDAY and then repeat weekly on WEDNESDAY!

    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '14:00:00', '15:00:00', '2022-11-16', null, 'WEDNESDAY'  FROM prison WHERE code = 'BLI';

    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '15:30:00', '16:30:00', '2022-11-16', null, 'WEDNESDAY'  FROM prison WHERE code = 'BLI';


    -- start_date FRIDAY then and repeat weekly on FRIDAY!

    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '14:00:00', '16:00:00', '2022-11-16', null, 'FRIDAY'  FROM prison WHERE code = 'BLI';

    -- start_date SATURDAY then and repeat weekly on SATURDAY!

    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '14:00:00', '15:00:00', '2022-11-16', null, 'SATURDAY'  FROM prison WHERE code = 'BLI';

    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '15:30:00', '16:30:00', '2022-11-16', null, 'SATURDAY'  FROM prison WHERE code = 'BLI';


    -- start_date SUNDAY then and repeat weekly on SUNDAY!

    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id,'Main Visits Hall', 'SUNDAY', 20, 1, '14:00:00', '15:00:00', '2022-11-16', null, 'SATURDAY'  FROM prison WHERE code = 'BLI';

    INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    SELECT id,'Main Visits Hall', 'SUNDAY', 20, 1, '15:30:00', '16:30:00', '2022-11-16', null, 'SATURDAY'  FROM prison WHERE code = 'BLI';


-- Commit the change
END;
-- Data for prisons visit schedule

-- Start a transaction
BEGIN;

    SET SCHEMA 'public';

    -- set up permitted_session_location

    -- use TRUNCATE rather than delete so indexes are re-set
    TRUNCATE TABLE  session_to_permitted_location;
    TRUNCATE TABLE  session_template;
    TRUNCATE TABLE  permitted_session_location;

    -- System Template section

        -- HEWELL

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


        -- Bristol


        INSERT INTO permitted_session_location (id,prison_id,level_one_code,description,type)
            VALUES 1,id, 'A, B, G, F, E, H', 'Bristol inclusion wings', 'INCLUDING'  FROM prison WHERE code = 'BLI';

        INSERT INTO permitted_session_location (id,prison_id,level_one_code,description,type)
            VALUES 2,id, 'D, F', 'Bristol inclusion wings', 'INCLUDING'  FROM prison WHERE code = 'BLI';

        INSERT INTO permitted_session_location (id,prison_id,level_one_code,level_two_code,level_three_code,description,type)
            VALUES 3,id, 'c','1','002, 003, 004, 005, 006, 007, 008', 'Bristol including wing c/1/*', 'INCLUDING'   FROM prison WHERE code = 'BLI';

        INSERT INTO permitted_session_location (id,prison_id,level_one_code,level_two_code,level_three_code,description,type)
            VALUES 4,id, 'c','2','025,026,027,028,029,030,031,032', 'Bristol including wing c/2/*', 'INCLUDING'   FROM prison WHERE code = 'BLI';

        INSERT INTO permitted_session_location (id,prison_id,level_one_code,level_two_code,level_three_code,description,type)
            VALUES 5,id, 'c','1','002,003,004,005,006,007,008', 'Bristol excluding wing c/1/*', 'EXCLUDING'  FROM prison WHERE code = 'BLI';

        INSERT INTO permitted_session_location (id,prison_id,level_one_code,level_two_code,level_three_code,description,type)
            VALUES 6,id, 'c','2','025,026,027,028,029,030,031,032', 'Bristol excluding wing c/2/*', 'EXCLUDING'   FROM prison WHERE code = 'BLI';

        -- start_date TUESDAY and then repeat weekly on TUESDAY!
        INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
            SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '14:00:00', '15:00:00', '2022-11-16', null, 'TUESDAY'  FROM prison WHERE code = 'BLI';

        INSERT INTO session_to_permitted_location (session_template_id, permitted_session_location_id)
            VALUES (pg_get_serial_sequence('session_template','id'),1)
                   (pg_get_serial_sequence('session_template','id'),5),
                   (pg_get_serial_sequence('session_template','id'),6);


        INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
        SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '15:30:00', '16:30:00', '2022-11-16', null, 'TUESDAY'  FROM prison WHERE code = 'BLI';

        INSERT INTO session_to_permitted_location (session_template_id, permitted_session_location_id)
            VALUES (pg_get_serial_sequence('session_template','id'),1)
                   (pg_get_serial_sequence('session_template','id'),5),
                   (pg_get_serial_sequence('session_template','id'),6);

        -- start_date WEDNESDAY and then repeat weekly on WEDNESDAY!

        INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
        SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '14:00:00', '15:00:00', '2022-11-16', null, 'WEDNESDAY'  FROM prison WHERE code = 'BLI';

        INSERT INTO session_to_permitted_location (session_template_id, permitted_session_location_id)
            VALUES (pg_get_serial_sequence('session_template','id'),1)
                   (pg_get_serial_sequence('session_template','id'),5),
                   (pg_get_serial_sequence('session_template','id'),6);

        INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
        SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '15:30:00', '16:30:00', '2022-11-16', null, 'WEDNESDAY'  FROM prison WHERE code = 'BLI';

        INSERT INTO session_to_permitted_location (session_template_id, permitted_session_location_id)
        VALUES (pg_get_serial_sequence('session_template','id'),2),
               (pg_get_serial_sequence('session_template','id'),3),
               (pg_get_serial_sequence('session_template','id'),4);

        -- start_date FRIDAY then and repeat weekly on FRIDAY!

        INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
        SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '14:00:00', '16:00:00', '2022-11-16', null, 'FRIDAY'  FROM prison WHERE code = 'BLI';

        INSERT INTO session_to_permitted_location (session_template_id, permitted_session_location_id)
            VALUES (pg_get_serial_sequence('session_template','id'),1)
                   (pg_get_serial_sequence('session_template','id'),5),
                   (pg_get_serial_sequence('session_template','id'),6);

        -- start_date SATURDAY then and repeat weekly on SATURDAY!

        INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
        SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '14:00:00', '15:00:00', '2022-11-16', null, 'SATURDAY'  FROM prison WHERE code = 'BLI';

        INSERT INTO session_to_permitted_location (session_template_id, permitted_session_location_id)
            VALUES (pg_get_serial_sequence('session_template','id'),1)
                   (pg_get_serial_sequence('session_template','id'),5),
                   (pg_get_serial_sequence('session_template','id'),6);

        INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
        SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '15:30:00', '16:30:00', '2022-11-16', null, 'SATURDAY'  FROM prison WHERE code = 'BLI';

        INSERT INTO session_to_permitted_location (session_template_id, permitted_session_location_id)
            VALUES  (pg_get_serial_sequence('session_template','id'),1)
                    (pg_get_serial_sequence('session_template','id'),5),
                    (pg_get_serial_sequence('session_template','id'),6);

        -- start_date SUNDAY then and repeat weekly on SUNDAY!

        INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
        SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '14:00:00', '15:00:00', '2022-11-16', null, 'SUNDAY'  FROM prison WHERE code = 'BLI';

        INSERT INTO session_to_permitted_location (session_template_id, permitted_session_location_id)
            VALUES  (pg_get_serial_sequence('session_template','id'),1)
                    (pg_get_serial_sequence('session_template','id'),5),
                    (pg_get_serial_sequence('session_template','id'),6);

        INSERT INTO session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
        SELECT id,'Main Visits Hall', 'SOCIAL', 20, 1, '15:30:00', '16:30:00', '2022-11-16', null, 'SUNDAY'  FROM prison WHERE code = 'BLI';

        INSERT INTO session_to_permitted_location (session_template_id, permitted_session_location_id)
            VALUES  (pg_get_serial_sequence('session_template','id'),2),
                    (pg_get_serial_sequence('session_template','id'),3),
                    (pg_get_serial_sequence('session_template','id'),4);

-- Commit the change
END;
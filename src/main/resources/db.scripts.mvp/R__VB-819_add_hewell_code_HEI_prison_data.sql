-- MVP data for Hewell visit schedule
-- Requirements set out in VB-819
--
--   Open slots = 30
--   Closed slots = 3
--
--   Monday -2-4pm
--   Tuesday - no visits
--   Wednesday - 2-4pm
--   Thursday - no visits
--   Friday - 9-11am  2-4pm
--   Saturday 2-4
--   Sunday 2-4
--
-- Open and closed slots run at the same time
--

-- Start a transaction
BEGIN;

    SET SCHEMA 'public';

    DELETE FROM session_template WHERE prison_id='HEI';

    INSERT INTO public.session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
    VALUES
        -- start_date MONDAY and then repeat weekly on Monday!
        ('HEI', 'Visits Main Room', 'SOCIAL', 30, 2, '13:45:00', '14:45:00', '2022-05-30', null, 'MONDAY'),
        -- start_date WEDNESDAY and then repeat weekly on WEDNESDAY!
        ('HEI', 'Visits Main Room', 'SOCIAL', 30, 2, '13:45:00', '14:45:00', '2022-06-01', null, 'WEDNESDAY'),
        -- start_date FRIDAY then and repeat weekly on FRIDAY!
        ('HEI', 'Visits Main Room', 'SOCIAL', 30, 2, '09:00:00', '10:00:00', '2022-06-03', null, 'FRIDAY'),
        -- start_date SATURDAY then and repeat weekly on SATURDAY!
        ('HEI', 'Visits Main Room', 'SOCIAL', 30, 2, '13:45:00', '14:45:00', '2022-06-04', null, 'SATURDAY'),
        -- start_date SUNDAY then and repeat weekly on SUNDAY!
        ('HEI', 'Visits Main Room', 'SOCIAL', 30, 2, '13:45:00', '14:45:00', '2022-06-05', null, 'SUNDAY');

-- Commit the change
END;
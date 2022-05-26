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

    INSERT INTO public.session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, start_date, expiry_date, frequency)
    VALUES
        -- start_date MONDAY and then repeat weekly on Monday!
        ('HEI', 'visits main room', 'SOCIAL', 30, 3, '14:00:00', '16:00:00', '2022-05-30', null, 'WEEKLY'),
        -- start_date WEDNESDAY and then repeat weekly on WEDNESDAY!
        ('HEI', 'visits main room', 'SOCIAL', 30, 3, '14:00:00', '16:00:00', '2022-06-01', null, 'WEEKLY'),
        -- start_date FRIDAY then and repeat weekly on FRIDAY!
        ('HEI', 'visits main room', 'SOCIAL', 30, 3, '09:00:00', '11:00:00', '2022-06-03', null, 'WEEKLY'),
        ('HEI', 'visits main room', 'SOCIAL', 30, 3, '14:00:00', '16:00:00', '2022-06-03', null, 'WEEKLY'),
        -- start_date SATURDAY then and repeat weekly on SATURDAY!
        ('HEI', 'visits main room', 'SOCIAL', 30, 3, '14:00:00', '16:00:00', '2022-06-04', null, 'WEEKLY'),
        -- start_date SUNDAY then and repeat weekly on SUNDAY!
        ('HEI', 'visits main room', 'SOCIAL', 30, 3, '14:00:00', '16:00:00', '2022-06-05', null, 'WEEKLY');

-- Commit the change
END;
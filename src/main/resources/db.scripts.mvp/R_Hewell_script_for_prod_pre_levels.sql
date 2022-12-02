-- VB-1477 Christmas Timetable for HMP Hewell
-- Start a transaction
BEGIN;

SET SCHEMA 'public';

DELETE FROM session_template WHERE prison_id='HEI';

INSERT INTO public.session_template (prison_id, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week)
VALUES
    ('HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2022-05-30','2022-12-18','MONDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2022-06-01','2022-12-18','WEDNESDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'09:00','10:00','2022-06-03','2022-12-18','FRIDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2022-06-04','2022-12-18','SATURDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2022-06-05','2022-12-18','SUNDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2022-12-19','2022-12-24','MONDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2022-12-21','2022-12-24','WEDNESDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'09:00','11:00','2022-12-23','2022-12-24','FRIDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2022-12-24','2022-12-24','SATURDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2022-12-28','2023-01-02','WEDNESDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'09:00','11:00','2022-12-30','2023-01-02','FRIDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2022-12-31','2023-01-02','SATURDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2023-01-01','2023-01-02','SUNDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2023-01-02','2023-01-02','MONDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2023-01-09',NULL,'MONDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2023-01-04',NULL,'WEDNESDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'09:00','10:00','2023-01-06',NULL,'FRIDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2023-01-07',NULL,'SATURDAY'),
    ('HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2023-01-08',NULL,'SUNDAY');

-- Commit the change
END;
BEGIN;

    SET SCHEMA 'public';
    UPDATE session_template SET valid_to_date = '2023-03-24' WHERE id=20 AND prison_id = 1;


    INSERT INTO session_template (prison_id,visit_room, visit_type, open_capacity, closed_capacity,enhanced, start_time, end_time, valid_from_date, valid_to_date, day_of_week,bi_weekly,name,reference)
    values (20,'Visits Main Room','SOCIAL',28,4,false,'13:45','14:45','2023-05-15','2023-05-15','MONDAY',false,'MONDAY 15th Special'      ,'-afe-insert.09'),
           (20,'Visits Main Room','SOCIAL',35,2,false,'13:45','14:45','2023-05-16',null        ,'MONDAY',false,'MONDAY, 2023-05-16, 13:45','-afe-insert.10');

END;
BEGIN;

    SET SCHEMA 'public';

    -- MHI Morton Hall https://dsdmoj.atlassian.net/browse/VB-2066
    -- MHI,Visits Main Hall,SOCIAL,14,2,FALSE,13:15:00,16:00:00,2023-04-07,,THURSDAY,FALSE,,,
    UPDATE session_template SET valid_to_date = '2023-05-31' WHERE id=140 AND prison_id = (select id from prison where code = 'MHI') and day_of_week = 'THURSDAY';


    INSERT INTO session_template (prison_id,visit_room, visit_type, open_capacity, closed_capacity,enhanced, start_time, end_time, valid_from_date, valid_to_date, day_of_week,bi_weekly,name,reference)
    values ((select id from prison where code = 'MHI'),'Visits Main Hall','SOCIAL',14,2,false,'13:15','16:00','2023-06-02',null,'THURSDAY',false,'THURSDAY, 2023-06-02','-afe-insert.11');


    -- PNI HMP Preston https://dsdmoj.atlassian.net/browse/VB-2209
    --PNI,Visits Main Hall,SOCIAL,32,8,FALSE,14:00:00,15:00:00,2023-01-23,,TUESDAY,FALSE,,,
    --PNI,Visits Main Hall,SOCIAL,32,8,FALSE,15:30:00,16:30:00,2023-01-23,,TUESDAY,FALSE,,,

    UPDATE session_template SET valid_to_date = '2023-05-15' WHERE id=112 AND prison_id = (select id from prison where code = 'PNI') and day_of_week = 'TUESDAY';
    UPDATE session_template SET valid_to_date = '2023-05-15' WHERE id=113 AND prison_id = (select id from prison where code = 'PNI') and day_of_week = 'TUESDAY';

    INSERT INTO session_template (prison_id,visit_room, visit_type, open_capacity, closed_capacity,enhanced, start_time, end_time, valid_from_date, valid_to_date, day_of_week,bi_weekly,name,reference)
    values ((select id from prison where code = 'PNI'),'Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-05-17','2023-06-14','TUESDAY',false,'TUESDAY 1','-afe-insert.12'),
           ((select id from prison where code = 'PNI'),'Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-05-17','2023-06-14','TUESDAY',false,'TUESDAY 2','-afe-insert.13'),
           ((select id from prison where code = 'PNI'),'Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-06-16',NULL,'TUESDAY',false,'TUESDAY 1','-afe-insert.14'),
           ((select id from prison where code = 'PNI'),'Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-06-16',NULL,'TUESDAY',false,'TUESDAY 2','-afe-insert.15');


END;
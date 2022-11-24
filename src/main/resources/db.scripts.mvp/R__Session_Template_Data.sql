-- This script clears certain tables and re-set auto id's to zero!
-- WARNING if the session template id's are used in other tables this script might have to change!
-- This is a temporary solution, and should be replaced by a JSON admin API!
-- Make sure prison table has the concerned prisons inserted before running this script!
BEGIN;

    SET SCHEMA 'public';

    -- Use TRUNCATE rather than delete so indexes are re-set
    TRUNCATE TABLE session_to_permitted_location RESTART IDENTITY CASCADE;
    TRUNCATE TABLE session_template  RESTART IDENTITY CASCADE;
    TRUNCATE TABLE permitted_session_location  RESTART IDENTITY CASCADE;


    -- Creating session template data
    CREATE TEMP TABLE tmp_session_template(
         id                serial        NOT NULL PRIMARY KEY,
         locationKeys      VARCHAR       ,
         prison_code       VARCHAR(6)    NOT NULL,
         prison_id         int    ,
         visit_room        VARCHAR(255)  NOT NULL,
         visit_type        VARCHAR(80)   NOT NULL,
         open_capacity     integer       NOT NULL,
         closed_capacity   integer       NOT NULL,
         start_time        time          NOT NULL,
         end_time          time          NOT NULL,
         valid_from_date   date          NOT NULL,
         valid_to_date     date          ,
         day_of_week       VARCHAR(40)   NOT NULL,
         bi_weekly         BOOLEAN       NOT NULL
        );

    INSERT INTO tmp_session_template (locationKeys,prison_code, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week,bi_weekly)
    VALUES
        (null,'HEI','Visits Main Room', 'SOCIAL', 30, 2, '13:45', '14:45', '2022-05-30', null, 'MONDAY',false),
        (null,'HEI','Visits Main Room', 'SOCIAL', 30, 2, '13:45', '14:45', '2022-06-01', null, 'WEDNESDAY',false),
        (null,'HEI','Visits Main Room', 'SOCIAL', 30, 2, '09:00', '10:00', '2022-06-03', null, 'FRIDAY',false),
        (null,'HEI','Visits Main Room', 'SOCIAL', 30, 2, '13:45', '14:45', '2022-06-04', null, 'SATURDAY',false),
        (null,'HEI','Visits Main Room', 'SOCIAL', 30, 2, '13:45', '14:45', '2022-06-05', null, 'SUNDAY',false),
        ('BLI_G1','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '14:00', '15:00', '2022-11-23', null, 'TUESDAY',true),
        ('BLI_G1','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '15:30', '16:30', '2022-11-23', null, 'TUESDAY',true),
        ('BLI_G1','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '14:00', '15:00', '2022-11-23', null, 'WEDNESDAY',true),
        ('BLI_G2','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '15:30', '16:30', '2022-11-23', null, 'WEDNESDAY',true),
        ('BLI_G1','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '14:00', '16:00', '2022-11-23', null, 'FRIDAY',true),
        ('BLI_G1','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '14:00', '15:00', '2022-11-23', null, 'SATURDAY',true),
        ('BLI_G1','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '15:30', '16:30', '2022-11-23', null, 'SATURDAY',true),
        ('BLI_G1','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '14:00', '15:00', '2022-11-23', null, 'SUNDAY',true),
        ('BLI_G2','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '15:30', '16:30', '2022-11-23', null, 'SUNDAY',true),
        ('BLI_G1','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '14:00', '15:00', '2022-11-30', null, 'TUESDAY',true),
        ('BLI_G2','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '15:30', '16:30', '2022-11-30', null, 'TUESDAY',true),
        ('BLI_G1','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '14:00', '16:00', '2022-11-30', null, 'WEDNESDAY',true),
        ('BLI_G2','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '14:00', '16:00', '2022-11-30', null, 'FRIDAY',true),
        ('BLI_G1','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '14:00', '15:00', '2022-11-30', null, 'SATURDAY',true),
        ('BLI_G1','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '15:30', '16:30', '2022-11-30', null, 'SATURDAY',true),
        ('BLI_G1','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '14:00', '15:00', '2022-11-30', null, 'SUNDAY',true),
        ('BLI_G1','BLI','Main Visits Hall', 'SOCIAL', 20, 1, '15:30', '16:30', '2022-11-30', null, 'SUNDAY',true);

    UPDATE tmp_session_template SET prison_id = prison.id FROM prison WHERE tmp_session_template.prison_code = prison.code;

    INSERT INTO session_template(id,visit_room,visit_type,open_capacity,closed_capacity,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id,bi_weekly)
    SELECT id,visit_room,visit_type,open_capacity,closed_capacity,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id,bi_weekly FROM tmp_session_template;
    ALTER SEQUENCE session_template_id_seq RESTART WITH 23;


    -- Create permitted session location data
    CREATE TABLE tmp_permitted_session_location (
                                                    id                serial        NOT NULL PRIMARY KEY,
                                                    key               VARCHAR(20)   NOT NULL,
                                                    prison_code       VARCHAR(6)    NOT NULL,
                                                    prison_id         int,
                                                    level_one_code    VARCHAR(10) NOT NULL,
                                                    level_two_code    VARCHAR(10),
                                                    level_three_code  VARCHAR(10),
                                                    level_four_code   VARCHAR(10)
    );

    INSERT INTO tmp_permitted_session_location (key,prison_code,level_one_code,level_two_code,level_three_code,level_four_code)
    VALUES
        ('BLI_G1','BLI','A',null,null,null),
        ('BLI_G1','BLI','B',null,null,null),
        ('BLI_G1','BLI','G',null,null,null),
        ('BLI_G1','BLI','F',null,null,null),
        ('BLI_G1','BLI','E',null,null,null),
        ('BLI_G1','BLI','H',null,null,null),
        ('BLI_G1','BLI','C','1','001',null),
        ('BLI_G1','BLI','C','1','009',null),
        ('BLI_G1','BLI','C','1','010',null),
        ('BLI_G1','BLI','C','1','011',null),
        ('BLI_G1','BLI','C','1','012',null),
        ('BLI_G1','BLI','C','1','013',null),
        ('BLI_G1','BLI','C','1','014',null),
        ('BLI_G1','BLI','C','1','015',null),
        ('BLI_G1','BLI','C','1','016',null),
        ('BLI_G1','BLI','C','1','017',null),
        ('BLI_G1','BLI','C','1','018',null),
        ('BLI_G1','BLI','C','1','019',null),
        ('BLI_G1','BLI','C','1','020',null),
        ('BLI_G1','BLI','C','1','021',null),
        ('BLI_G1','BLI','C','1','022',null),
        ('BLI_G1','BLI','C','1','023',null),
        ('BLI_G1','BLI','C','1','024',null),
        ('BLI_G1','BLI','C','1','025',null),
        ('BLI_G1','BLI','C','1','026',null),
        ('BLI_G1','BLI','C','1','027',null),
        ('BLI_G1','BLI','C','1','028',null),
        ('BLI_G1','BLI','C','1','029',null),
        ('BLI_G1','BLI','C','1','030',null),
        ('BLI_G1','BLI','C','1','031',null),
        ('BLI_G1','BLI','C','1','032',null),
        ('BLI_G1','BLI','C','2','001',null),
        ('BLI_G1','BLI','C','2','002',null),
        ('BLI_G1','BLI','C','2','003',null),
        ('BLI_G1','BLI','C','2','004',null),
        ('BLI_G1','BLI','C','2','005',null),
        ('BLI_G1','BLI','C','2','006',null),
        ('BLI_G1','BLI','C','2','007',null),
        ('BLI_G1','BLI','C','2','008',null),
        ('BLI_G1','BLI','C','2','009',null),
        ('BLI_G1','BLI','C','2','010',null),
        ('BLI_G1','BLI','C','2','011',null),
        ('BLI_G1','BLI','C','2','012',null),
        ('BLI_G1','BLI','C','2','013',null),
        ('BLI_G1','BLI','C','2','014',null),
        ('BLI_G1','BLI','C','2','015',null),
        ('BLI_G1','BLI','C','2','016',null),
        ('BLI_G1','BLI','C','2','017',null),
        ('BLI_G1','BLI','C','2','018',null),
        ('BLI_G1','BLI','C','2','019',null),
        ('BLI_G1','BLI','C','2','020',null),
        ('BLI_G1','BLI','C','2','021',null),
        ('BLI_G1','BLI','C','2','022',null),
        ('BLI_G1','BLI','C','2','023',null),
        ('BLI_G1','BLI','C','2','024',null),
        ('BLI_G1','BLI','C','3','001',null),
        ('BLI_G1','BLI','C','3','002',null),
        ('BLI_G1','BLI','C','3','003',null),
        ('BLI_G1','BLI','C','3','004',null),
        ('BLI_G1','BLI','C','3','005',null),
        ('BLI_G1','BLI','C','3','006',null),
        ('BLI_G1','BLI','C','3','007',null),
        ('BLI_G1','BLI','C','3','008',null),
        ('BLI_G1','BLI','C','3','009',null),
        ('BLI_G1','BLI','C','3','010',null),
        ('BLI_G1','BLI','C','3','011',null),
        ('BLI_G1','BLI','C','3','012',null),
        ('BLI_G1','BLI','C','3','013',null),
        ('BLI_G1','BLI','C','3','014',null),
        ('BLI_G1','BLI','C','3','015',null),
        ('BLI_G1','BLI','C','3','016',null),
        ('BLI_G1','BLI','C','3','017',null),
        ('BLI_G1','BLI','C','3','018',null),
        ('BLI_G1','BLI','C','3','019',null),
        ('BLI_G1','BLI','C','3','020',null),
        ('BLI_G1','BLI','C','3','021',null),
        ('BLI_G1','BLI','C','3','022',null),
        ('BLI_G1','BLI','C','3','023',null),
        ('BLI_G1','BLI','C','3','024',null),
        ('BLI_G2','BLI','D',null,null,null),
        ('BLI_G2','BLI','F',null,null,null),
        ('BLI_G2','BLI','C','1','002',null),
        ('BLI_G2','BLI','C','1','003',null),
        ('BLI_G2','BLI','C','1','004',null),
        ('BLI_G2','BLI','C','1','005',null),
        ('BLI_G2','BLI','C','1','006',null),
        ('BLI_G2','BLI','C','1','007',null),
        ('BLI_G2','BLI','C','1','008',null),
        ('BLI_G2','BLI','C','2','025',null),
        ('BLI_G2','BLI','C','2','026',null),
        ('BLI_G2','BLI','C','2','027',null),
        ('BLI_G2','BLI','C','2','028',null),
        ('BLI_G2','BLI','C','2','029',null),
        ('BLI_G2','BLI','C','2','030',null),
        ('BLI_G2','BLI','C','2','031',null),
        ('BLI_G2','BLI','C','2','032',null);

    UPDATE tmp_permitted_session_location SET prison_id = prison.id FROM prison WHERE tmp_permitted_session_location.prison_code = prison.code;

    INSERT INTO permitted_session_location(id,prison_id,level_one_code,level_two_code,level_three_code,level_four_code)
    SELECT id,prison_id,level_one_code,level_two_code,level_three_code,level_four_code FROM tmp_permitted_session_location;

    ALTER SEQUENCE permitted_session_location_id_seq RESTART WITH 97;


    -- Create link table data
    INSERT INTO session_to_permitted_location(session_template_id, permitted_session_location_id)
    SELECT st.id, l.id FROM tmp_session_template st
                                JOIN tmp_permitted_session_location l ON POSITION(l.key  IN st.locationKeys)<>0 ORDER BY st.id,l.id;


    -- Drop temporary tables
    DROP TABLE tmp_session_template;
    DROP TABLE tmp_permitted_session_location;

END;

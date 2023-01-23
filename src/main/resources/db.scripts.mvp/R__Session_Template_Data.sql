-- This script clears certain tables and re-set auto id's to zero!
-- WARNING if the session template id's are used in other tables this script might have to change!
-- This is a temporary solution, and should be replaced by a JSON admin API!
-- Make sure prison table has the concerned prisons inserted before running this script!
-- See for instructions https://dsdmoj.atlassian.net/wiki/spaces/PSCH/pages/4239622317/SQL+SessionTemplate+Generator
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
             enhanced          BOOLEAN       NOT NULL,
             start_time        time          NOT NULL,
             end_time          time          NOT NULL,
             valid_from_date   date          NOT NULL,
             valid_to_date     date          ,
             day_of_week       VARCHAR(40)   NOT NULL,
             bi_weekly         BOOLEAN       NOT NULL
            );

    INSERT INTO tmp_session_template (locationKeys,prison_code, visit_room, visit_type, open_capacity, closed_capacity,enhanced, start_time, end_time, valid_from_date, valid_to_date, day_of_week,bi_weekly)
    VALUES
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2022-05-30','2022-12-18','MONDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2022-06-01','2022-12-18','WEDNESDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'09:00','10:00','2022-06-03','2022-12-18','FRIDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2022-06-04','2022-12-18','SATURDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2022-06-05','2022-12-18','SUNDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2022-12-19','2022-12-24','MONDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2022-12-21','2022-12-24','WEDNESDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'09:00','11:00','2022-12-23','2022-12-24','FRIDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2022-12-24','2022-12-24','SATURDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2022-12-28','2023-01-02','WEDNESDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'09:00','11:00','2022-12-30','2023-01-02','FRIDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2022-12-31','2023-01-02','SATURDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2023-01-01','2023-01-02','SUNDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2023-01-02','2023-01-02','MONDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2023-01-09',NULL,'MONDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2023-01-04',NULL,'WEDNESDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'09:00','10:00','2023-01-06',NULL,'FRIDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2023-01-07',NULL,'SATURDAY',false),
        (NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2023-01-08',NULL,'SUNDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-05','2022-12-18','TUESDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-05','2022-12-18','TUESDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-05','2022-12-18','WEDNESDAY',true),
        ('BLI_G2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-05','2022-12-18','WEDNESDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2022-12-05','2022-12-18','FRIDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-05','2022-12-18','SATURDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-05','2022-12-18','SATURDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-05','2022-12-18','SUNDAY',true),
        ('BLI_G2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-05','2022-12-18','SUNDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-11-28','2022-12-18','TUESDAY',true),
        ('BLI_G2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-11-28','2022-12-18','TUESDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2022-11-28','2022-12-18','WEDNESDAY',true),
        ('BLI_G2','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2022-11-28','2022-12-18','FRIDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-11-28','2022-12-18','SATURDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-11-28','2022-12-18','SATURDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-11-28','2022-12-18','SUNDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-11-28','2022-12-18','SUNDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-20','2022-12-20','TUESDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-20','2022-12-20','TUESDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-21','2022-12-21','WEDNESDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-21','2022-12-21','WEDNESDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2022-12-23','2022-12-23','FRIDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-24','2022-12-24','SATURDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-24','2022-12-24','SATURDAY',false),
        ('BLI_G2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-27','2022-12-27','TUESDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2022-12-28','2022-12-28','WEDNESDAY',false),
        ('BLI_G2','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2022-12-30','2022-12-30','FRIDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'09:30','10:30','2022-12-31','2022-12-31','SATURDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-31','2022-12-31','SATURDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-01','2023-01-01','SUNDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-01','2023-01-01','SUNDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-03','2023-01-03','TUESDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-03','2023-01-03','TUESDAY',false),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-04',NULL,'TUESDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-04',NULL,'TUESDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-04',NULL,'WEDNESDAY',true),
        ('BLI_G2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-04',NULL,'WEDNESDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2023-01-04',NULL,'FRIDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-04',NULL,'SATURDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-04',NULL,'SATURDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-04',NULL,'SUNDAY',true),
        ('BLI_G2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-04',NULL,'SUNDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-09',NULL,'TUESDAY',true),
        ('BLI_G2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-09',NULL,'TUESDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2023-01-09',NULL,'WEDNESDAY',true),
        ('BLI_G2','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2023-01-09',NULL,'FRIDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-09',NULL,'SATURDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-09',NULL,'SATURDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-09',NULL,'SUNDAY',true),
        ('BLI_G1','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-09',NULL,'SUNDAY',true),
        (NULL,'CFI','Visits Main Hall','SOCIAL',35,0,false,'13:45','15:45','2023-01-23',NULL,'MONDAY',false),
        (NULL,'CFI','Visits Main Hall','SOCIAL',35,0,false,'13:45','15:45','2023-01-23',NULL,'TUESDAY',false),
        (NULL,'CFI','Visits Main Hall','SOCIAL',0,2,false,'13:45','14:45','2023-01-23',NULL,'TUESDAY',false),
        (NULL,'CFI','Visits Main Hall','SOCIAL',15,0,true,'18:00','18:45','2023-01-23',NULL,'TUESDAY',false),
        (NULL,'CFI','Visits Main Hall','SOCIAL',35,0,false,'13:45','15:45','2023-01-23',NULL,'WEDNESDAY',false),
        (NULL,'CFI','Visits Main Hall','SOCIAL',0,2,false,'13:45','14:45','2023-01-23',NULL,'WEDNESDAY',false),
        (NULL,'CFI','Visits Main Hall','SOCIAL',35,0,false,'13:45','15:45','2023-01-23',NULL,'THURSDAY',false),
        (NULL,'CFI','Visits Main Hall','SOCIAL',0,2,false,'13:45','14:45','2023-01-23',NULL,'THURSDAY',false),
        (NULL,'CFI','Visits Main Hall','SOCIAL',35,0,false,'13:45','15:45','2023-01-23',NULL,'FRIDAY',false),
        (NULL,'CFI','Visits Main Hall','SOCIAL',0,2,false,'13:45','14:45','2023-01-23',NULL,'FRIDAY',false),
        (NULL,'CFI','Visits Main Hall','SOCIAL',35,0,false,'09:30','11:30','2023-01-23',NULL,'SATURDAY',false),
        (NULL,'CFI','Visits Main Hall','SOCIAL',35,0,false,'13:45','15:45','2023-01-23',NULL,'SATURDAY',false),
        (NULL,'CFI','Visits Main Hall','SOCIAL',35,0,false,'13:45','15:45','2023-01-23',NULL,'SUNDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'09:30','11:30','2023-01-23',NULL,'MONDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'13:30','14:30','2023-01-23',NULL,'MONDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'15:30','16:30','2023-01-23',NULL,'MONDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'09:00','10:00','2023-01-23',NULL,'TUESDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'10:30','11:30','2023-01-23',NULL,'TUESDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'13:30','14:30','2023-01-23',NULL,'TUESDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'15:30','16:30','2023-01-23',NULL,'TUESDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'09:30','11:30','2023-01-23',NULL,'WEDNESDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'13:30','14:30','2023-01-23',NULL,'WEDNESDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'15:30','16:30','2023-01-23',NULL,'WEDNESDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'09:00','10:00','2023-01-23',NULL,'THURSDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'10:30','11:30','2023-01-23',NULL,'THURSDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'13:30','14:30','2023-01-23',NULL,'THURSDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'15:30','16:30','2023-01-23',NULL,'THURSDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'09:00','10:00','2023-01-23',NULL,'SATURDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'10:30','11:30','2023-01-23',NULL,'SATURDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'13:30','14:30','2023-01-23',NULL,'SATURDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'15:30','16:30','2023-01-23',NULL,'SATURDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'09:00','10:00','2023-01-23',NULL,'SUNDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'10:30','11:30','2023-01-23',NULL,'SUNDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'13:30','14:30','2023-01-23',NULL,'SUNDAY',false),
        (NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'15:30','16:30','2023-01-23',NULL,'SUNDAY',false),
        (NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-01-23',NULL,'MONDAY',false),
        (NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-01-23',NULL,'MONDAY',false),
        (NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-01-23',NULL,'TUESDAY',false),
        (NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-01-23',NULL,'TUESDAY',false),
        (NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-01-23',NULL,'THURSDAY',false),
        (NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-01-23',NULL,'THURSDAY',false),
        (NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-01-23',NULL,'FRIDAY',false),
        (NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-01-23',NULL,'FRIDAY',false),
        (NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-01-23',NULL,'SATURDAY',false),
        (NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-01-23',NULL,'SATURDAY',false),
        (NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-01-23',NULL,'SUNDAY',false),
        (NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-01-23',NULL,'SUNDAY',false)
    ;


    UPDATE tmp_session_template SET prison_id = prison.id FROM prison WHERE tmp_session_template.prison_code = prison.code;

    INSERT INTO session_template(id,visit_room,visit_type,open_capacity,closed_capacity,enhanced,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id,bi_weekly)
    SELECT id,visit_room,visit_type,open_capacity,closed_capacity,enhanced,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id,bi_weekly FROM tmp_session_template order by id;
    ALTER SEQUENCE session_template_id_seq RESTART WITH  117;


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
        ('BLI_G1','BLI','A',NULL,NULL, NULL),
        ('BLI_G1','BLI','B',NULL,NULL, NULL),
        ('BLI_G1','BLI','G',NULL,NULL, NULL),
        ('BLI_G1','BLI','F',NULL,NULL, NULL),
        ('BLI_G1','BLI','E',NULL,NULL, NULL),
        ('BLI_G1','BLI','H',NULL,NULL, NULL),
        ('BLI_G1','BLI','C','1','001', NULL),
        ('BLI_G1','BLI','C','1','002', NULL),
        ('BLI_G1','BLI','C','1','003', NULL),
        ('BLI_G1','BLI','C','1','004', NULL),
        ('BLI_G1','BLI','C','1','005', NULL),
        ('BLI_G1','BLI','C','1','006', NULL),
        ('BLI_G1','BLI','C','1','007', NULL),
        ('BLI_G1','BLI','C','1','008', NULL),
        ('BLI_G1','BLI','C','1','009', NULL),
        ('BLI_G1','BLI','C','1','010', NULL),
        ('BLI_G1','BLI','C','1','011', NULL),
        ('BLI_G1','BLI','C','1','012', NULL),
        ('BLI_G1','BLI','C','1','013', NULL),
        ('BLI_G1','BLI','C','1','014', NULL),
        ('BLI_G1','BLI','C','1','015', NULL),
        ('BLI_G1','BLI','C','1','016', NULL),
        ('BLI_G1','BLI','C','1','017', NULL),
        ('BLI_G1','BLI','C','1','018', NULL),
        ('BLI_G1','BLI','C','1','019', NULL),
        ('BLI_G1','BLI','C','1','020', NULL),
        ('BLI_G1','BLI','C','1','021', NULL),
        ('BLI_G1','BLI','C','1','022', NULL),
        ('BLI_G1','BLI','C','1','023', NULL),
        ('BLI_G1','BLI','C','1','024', NULL),
        ('BLI_G1','BLI','C','1','025', NULL),
        ('BLI_G1','BLI','C','1','026', NULL),
        ('BLI_G1','BLI','C','1','027', NULL),
        ('BLI_G1','BLI','C','1','028', NULL),
        ('BLI_G1','BLI','C','1','029', NULL),
        ('BLI_G1','BLI','C','1','030', NULL),
        ('BLI_G1','BLI','C','1','031', NULL),
        ('BLI_G1','BLI','C','1','032', NULL),
        ('BLI_G1','BLI','C','2','009', NULL),
        ('BLI_G1','BLI','C','2','010', NULL),
        ('BLI_G1','BLI','C','2','011', NULL),
        ('BLI_G1','BLI','C','2','012', NULL),
        ('BLI_G1','BLI','C','2','013', NULL),
        ('BLI_G1','BLI','C','2','014', NULL),
        ('BLI_G1','BLI','C','2','015', NULL),
        ('BLI_G1','BLI','C','2','016', NULL),
        ('BLI_G1','BLI','C','2','017', NULL),
        ('BLI_G1','BLI','C','2','018', NULL),
        ('BLI_G1','BLI','C','2','019', NULL),
        ('BLI_G1','BLI','C','2','020', NULL),
        ('BLI_G1','BLI','C','2','021', NULL),
        ('BLI_G1','BLI','C','2','022', NULL),
        ('BLI_G1','BLI','C','2','023', NULL),
        ('BLI_G1','BLI','C','2','024', NULL),
        ('BLI_G1','BLI','C','3','001', NULL),
        ('BLI_G1','BLI','C','3','002', NULL),
        ('BLI_G1','BLI','C','3','003', NULL),
        ('BLI_G1','BLI','C','3','004', NULL),
        ('BLI_G1','BLI','C','3','005', NULL),
        ('BLI_G1','BLI','C','3','006', NULL),
        ('BLI_G1','BLI','C','3','007', NULL),
        ('BLI_G1','BLI','C','3','008', NULL),
        ('BLI_G1','BLI','C','3','009', NULL),
        ('BLI_G1','BLI','C','3','010', NULL),
        ('BLI_G1','BLI','C','3','011', NULL),
        ('BLI_G1','BLI','C','3','012', NULL),
        ('BLI_G1','BLI','C','3','013', NULL),
        ('BLI_G1','BLI','C','3','014', NULL),
        ('BLI_G1','BLI','C','3','015', NULL),
        ('BLI_G1','BLI','C','3','016', NULL),
        ('BLI_G1','BLI','C','3','017', NULL),
        ('BLI_G1','BLI','C','3','018', NULL),
        ('BLI_G1','BLI','C','3','019', NULL),
        ('BLI_G1','BLI','C','3','020', NULL),
        ('BLI_G1','BLI','C','3','021', NULL),
        ('BLI_G1','BLI','C','3','022', NULL),
        ('BLI_G1','BLI','C','3','023', NULL),
        ('BLI_G1','BLI','C','3','024', NULL),
        ('BLI_G2','BLI','D',NULL,NULL, NULL),
        ('BLI_G2','BLI','F',NULL,NULL, NULL),
        ('BLI_G2','BLI','C','2','001', NULL),
        ('BLI_G2','BLI','C','2','002', NULL),
        ('BLI_G2','BLI','C','2','003', NULL),
        ('BLI_G2','BLI','C','2','004', NULL),
        ('BLI_G2','BLI','C','2','005', NULL),
        ('BLI_G2','BLI','C','2','006', NULL),
        ('BLI_G2','BLI','C','2','007', NULL),
        ('BLI_G2','BLI','C','2','008', NULL),
        ('BLI_G2','BLI','C','2','025', NULL),
        ('BLI_G2','BLI','C','2','026', NULL),
        ('BLI_G2','BLI','C','2','027', NULL),
        ('BLI_G2','BLI','C','2','028', NULL),
        ('BLI_G2','BLI','C','2','029', NULL),
        ('BLI_G2','BLI','C','2','030', NULL),
        ('BLI_G2','BLI','C','2','031', NULL),
        ('BLI_G2','BLI','C','2','032', NULL)
    ;

    UPDATE tmp_permitted_session_location SET prison_id = prison.id FROM prison WHERE tmp_permitted_session_location.prison_code = prison.code;

    INSERT INTO permitted_session_location(id,prison_id,level_one_code,level_two_code,level_three_code,level_four_code)
    SELECT id,prison_id,level_one_code,level_two_code,level_three_code,level_four_code FROM tmp_permitted_session_location order by id;

    ALTER SEQUENCE permitted_session_location_id_seq RESTART WITH 97;


    -- Create link table data
    INSERT INTO session_to_permitted_location(session_template_id, location_group ,permitted_session_location_id)
    SELECT st.id, l.key, l.id FROM tmp_session_template st
                                       JOIN tmp_permitted_session_location l ON POSITION(l.key  IN st.locationKeys)<>0 ORDER BY st.id,l.id;


    -- Drop temporary tables
    DROP TABLE tmp_session_template;
    DROP TABLE tmp_permitted_session_location;

END;

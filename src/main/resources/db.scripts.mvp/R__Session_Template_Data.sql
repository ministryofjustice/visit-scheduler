    -- This script clears certain tables and re-set auto id's to zero!
    -- WARNING if the session template id's are used in other tables this script might have to change!
    -- This is a temporary solution, and should be replaced by a JSON admin API!
    -- Make sure prison table has the concerned prisons inserted before running this script!
    -- See for instructions https://dsdmoj.atlassian.net/wiki/spaces/PSCH/pages/4239622317/SQL+SessionTemplate+Generator
    BEGIN;

        SET SCHEMA 'public';

        -- Use TRUNCATE rather than delete so indexes are re-set
        TRUNCATE TABLE session_to_location_group RESTART IDENTITY CASCADE;
        TRUNCATE TABLE session_location_group RESTART IDENTITY CASCADE;
        TRUNCATE TABLE session_to_category_group RESTART IDENTITY CASCADE;
        TRUNCATE TABLE session_category_group RESTART IDENTITY CASCADE;
        TRUNCATE TABLE session_to_incentive_group RESTART IDENTITY CASCADE;
        TRUNCATE TABLE session_incentive_group RESTART IDENTITY CASCADE;
        TRUNCATE TABLE session_template  RESTART IDENTITY CASCADE;
        TRUNCATE TABLE permitted_session_location  RESTART IDENTITY CASCADE;
        TRUNCATE TABLE session_prisoner_category  RESTART IDENTITY CASCADE;
        TRUNCATE TABLE session_prisoner_incentive  RESTART IDENTITY CASCADE;

        INSERT INTO prison(code, active) SELECT 'HEI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'HEI');
        INSERT INTO prison(code, active) SELECT 'BLI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'BLI');
        INSERT INTO prison(code, active) SELECT 'CFI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'CFI');
        INSERT INTO prison(code, active) SELECT 'WWI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'WWI');
        INSERT INTO prison(code, active) SELECT 'PNI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'PNI');
        INSERT INTO prison(code, active) SELECT 'EWI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'EWI');
        INSERT INTO prison(code, active) SELECT 'DHI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'DHI');
        INSERT INTO prison(code, active) SELECT 'MHI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'MHI');
        INSERT INTO prison(code, active) SELECT 'BNI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'BNI');
        INSERT INTO prison(code, active) SELECT 'FNI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'FNI');
        INSERT INTO prison(code, active) SELECT 'LNI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'LNI');
        INSERT INTO prison(code, active) SELECT 'FHI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'FHI');
        INSERT INTO prison(code, active) SELECT 'ESI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'ESI');
        INSERT INTO prison(code, active) SELECT 'BSI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'BSI');
        INSERT INTO prison(code, active) SELECT 'AGI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'AGI');
        INSERT INTO prison(code, active) SELECT 'DMI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'DMI');

        -- Creating session template data
        CREATE TEMP TABLE tmp_session_template(
             id                serial        NOT NULL PRIMARY KEY,
             locationKeys      VARCHAR       ,
             categoryKeys      VARCHAR       ,
             incentiveLevelKeys VARCHAR      ,
             prison_code       VARCHAR(6)    NOT NULL,
             prison_id         int    ,
             visit_room    VARCHAR(255),
             visit_type        VARCHAR(80)   NOT NULL,
             open_capacity     integer       NOT NULL,
             closed_capacity   integer       NOT NULL,
             start_time        time          NOT NULL,
             end_time          time          NOT NULL,
             valid_from_date   date          NOT NULL,
             valid_to_date     date          ,
             day_of_week       VARCHAR(40)   NOT NULL,
             weekly_frequency  integer       NOT NULL,
             name              varchar(100)  NOT NULL,
             active            BOOLEAN NOT NULL
            );

        INSERT INTO tmp_session_template (locationKeys, categoryKeys, incentiveLevelKeys, prison_code, visit_room, visit_type, open_capacity, closed_capacity, start_time, end_time, valid_from_date, valid_to_date, day_of_week, weekly_frequency, name, active)
        VALUES
            (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2022-05-30','2022-12-18','MONDAY',1,'MONDAY, 2022-05-30, 13:45',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2022-06-01','2022-12-18','WEDNESDAY',1,'WEDNESDAY, 2022-06-01, 13:45',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'09:00','10:00','2022-06-03','2022-12-18','FRIDAY',1,'FRIDAY, 2022-06-03, 09:00',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2022-06-04','2022-12-18','SATURDAY',1,'SATURDAY, 2022-06-04, 13:45',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2022-06-05','2022-12-18','SUNDAY',1,'SUNDAY, 2022-06-05, 13:45',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2022-12-19','2022-12-24','MONDAY',1,'MONDAY, 2022-12-19, 14:00',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2022-12-21','2022-12-24','WEDNESDAY',1,'WEDNESDAY, 2022-12-21, 14:00',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'09:00','11:00','2022-12-23','2022-12-24','FRIDAY',1,'FRIDAY, 2022-12-23, 09:00',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2022-12-24','2022-12-24','SATURDAY',1,'SATURDAY, 2022-12-24, 14:00',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2022-12-28','2023-01-02','WEDNESDAY',1,'WEDNESDAY, 2022-12-28, 14:00',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'09:00','11:00','2022-12-30','2023-01-02','FRIDAY',1,'FRIDAY, 2022-12-30, 09:00',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2022-12-31','2023-01-02','SATURDAY',1,'SATURDAY, 2022-12-31, 14:00',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2023-01-01','2023-01-02','SUNDAY',1,'SUNDAY, 2023-01-01, 14:00',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'14:00','16:00','2023-01-02','2023-01-02','MONDAY',1,'MONDAY, 2023-01-02, 14:00',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2023-01-09','2023-03-20','MONDAY',1,'MONDAY, 2023-01-09, 13:45',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2023-01-04','2023-03-20','WEDNESDAY',1,'WEDNESDAY, 2023-01-04, 13:45',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'09:00','10:00','2023-01-06','2023-03-20','FRIDAY',1,'FRIDAY, 2023-01-06, 09:00',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2023-01-07','2023-03-20','SATURDAY',1,'SATURDAY, 2023-01-07, 13:45',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',30,2,'13:45','14:45','2023-01-08','2023-03-20','SUNDAY',1,'SUNDAY, 2023-01-08, 13:45',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',35,2,'13:45','14:45','2023-03-21','2023-05-14','MONDAY',1,'MONDAY, 2023-03-21, 13:45',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',35,2,'13:45','14:45','2023-03-21',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-03-21, 13:45',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',35,2,'09:00','10:00','2023-03-21',NULL,'FRIDAY',1,'FRIDAY, 2023-03-21, 09:00',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',35,2,'13:45','14:45','2023-03-21',NULL,'SATURDAY',1,'SATURDAY, 2023-03-21, 13:45',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',35,2,'13:45','14:45','2023-03-21',NULL,'SUNDAY',1,'SUNDAY, 2023-03-21, 13:45',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',28,4,'13:45','14:45','2023-05-15','2023-05-15','MONDAY',1,'MONDAY, 2023-05-15, 13:45',true),
                    (NULL,NULL,NULL,'HEI','Visits Main Room','SOCIAL',35,2,'13:45','14:45','2023-05-16',NULL,'MONDAY',1,'MONDAY, 2023-05-16, 13:45',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','15:00','2022-12-05','2022-12-18','TUESDAY',2,'TUESDAY, 2022-12-05, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'15:30','16:30','2022-12-05','2022-12-18','TUESDAY',2,'TUESDAY, 2022-12-05, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','15:00','2022-12-05','2022-12-18','WEDNESDAY',2,'WEDNESDAY, 2022-12-05, 14:00',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'15:30','16:30','2022-12-05','2022-12-18','WEDNESDAY',2,'WEDNESDAY, 2022-12-05, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','16:00','2022-12-05','2022-12-18','FRIDAY',2,'FRIDAY, 2022-12-05, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','15:00','2022-12-05','2022-12-18','SATURDAY',2,'SATURDAY, 2022-12-05, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'15:30','16:30','2022-12-05','2022-12-18','SATURDAY',2,'SATURDAY, 2022-12-05, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','15:00','2022-12-05','2022-12-18','SUNDAY',2,'SUNDAY, 2022-12-05, 14:00',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'15:30','16:30','2022-12-05','2022-12-18','SUNDAY',2,'SUNDAY, 2022-12-05, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','15:00','2022-11-28','2022-12-18','TUESDAY',2,'TUESDAY, 2022-11-28, 14:00',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'15:30','16:30','2022-11-28','2022-12-18','TUESDAY',2,'TUESDAY, 2022-11-28, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','16:00','2022-11-28','2022-12-18','WEDNESDAY',2,'WEDNESDAY, 2022-11-28, 14:00',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','16:00','2022-11-28','2022-12-18','FRIDAY',2,'FRIDAY, 2022-11-28, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','15:00','2022-11-28','2022-12-18','SATURDAY',2,'SATURDAY, 2022-11-28, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'15:30','16:30','2022-11-28','2022-12-18','SATURDAY',2,'SATURDAY, 2022-11-28, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','15:00','2022-11-28','2022-12-18','SUNDAY',2,'SUNDAY, 2022-11-28, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'15:30','16:30','2022-11-28','2022-12-18','SUNDAY',2,'SUNDAY, 2022-11-28, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','15:00','2022-12-20','2022-12-20','TUESDAY',1,'TUESDAY, 2022-12-20, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'15:30','16:30','2022-12-20','2022-12-20','TUESDAY',1,'TUESDAY, 2022-12-20, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','15:00','2022-12-21','2022-12-21','WEDNESDAY',1,'WEDNESDAY, 2022-12-21, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'15:30','16:30','2022-12-21','2022-12-21','WEDNESDAY',1,'WEDNESDAY, 2022-12-21, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','16:00','2022-12-23','2022-12-23','FRIDAY',1,'FRIDAY, 2022-12-23, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','15:00','2022-12-24','2022-12-24','SATURDAY',1,'SATURDAY, 2022-12-24, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'15:30','16:30','2022-12-24','2022-12-24','SATURDAY',1,'SATURDAY, 2022-12-24, 15:30',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'15:30','16:30','2022-12-27','2022-12-27','TUESDAY',1,'TUESDAY, 2022-12-27, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','16:00','2022-12-28','2022-12-28','WEDNESDAY',1,'WEDNESDAY, 2022-12-28, 14:00',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','16:00','2022-12-30','2022-12-30','FRIDAY',1,'FRIDAY, 2022-12-30, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'09:30','10:30','2022-12-31','2022-12-31','SATURDAY',1,'SATURDAY, 2022-12-31, 09:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','15:00','2022-12-31','2022-12-31','SATURDAY',1,'SATURDAY, 2022-12-31, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','15:00','2023-01-01','2023-01-01','SUNDAY',1,'SUNDAY, 2023-01-01, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'15:30','16:30','2023-01-01','2023-01-01','SUNDAY',1,'SUNDAY, 2023-01-01, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'14:00','15:00','2023-01-03','2023-01-03','TUESDAY',1,'TUESDAY, 2023-01-03, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','Visits Main Hall','SOCIAL',20,1,'15:30','16:30','2023-01-03','2023-01-03','TUESDAY',1,'TUESDAY, 2023-01-03, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',20,1,'14:00','15:00','2023-01-04','2023-05-21','TUESDAY',2,'TUESDAY, 2023-01-04, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',20,1,'15:30','16:30','2023-01-04','2023-05-21','TUESDAY',2,'TUESDAY, 2023-01-04, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',20,1,'14:00','15:00','2023-01-04','2023-05-21','WEDNESDAY',2,'WEDNESDAY, 2023-01-04, 14:00',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','WC L2 CC 1TO8 25TO32','SOCIAL',20,1,'15:30','16:30','2023-01-04','2023-05-21','WEDNESDAY',2,'WEDNESDAY, 2023-01-04, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',20,1,'14:00','16:00','2023-01-04','2023-05-21','FRIDAY',2,'FRIDAY, 2023-01-04, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',20,1,'14:00','15:00','2023-01-04','2023-05-21','SATURDAY',2,'SATURDAY, 2023-01-04, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',20,1,'15:30','16:30','2023-01-04','2023-05-21','SATURDAY',2,'SATURDAY, 2023-01-04, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',20,1,'14:00','15:00','2023-01-04','2023-05-21','SUNDAY',2,'SUNDAY, 2023-01-04, 14:00',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','WC L2 CC 1TO8 25TO32','SOCIAL',20,1,'15:30','16:30','2023-01-04','2023-05-21','SUNDAY',2,'SUNDAY, 2023-01-04, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',20,1,'14:00','15:00','2023-01-09','2023-05-21','TUESDAY',2,'TUESDAY, 2023-01-09, 14:00',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','WC L2 CC 1TO8 25TO32','SOCIAL',20,1,'15:30','16:30','2023-01-09','2023-05-21','TUESDAY',2,'TUESDAY, 2023-01-09, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',20,1,'14:00','16:00','2023-01-09','2023-05-21','WEDNESDAY',2,'WEDNESDAY, 2023-01-09, 14:00',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','WC L2 CC 1TO8 25TO32','SOCIAL',20,1,'14:00','16:00','2023-01-09','2023-05-21','FRIDAY',2,'FRIDAY, 2023-01-09, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',20,1,'14:00','15:00','2023-01-09','2023-05-21','SATURDAY',2,'SATURDAY, 2023-01-09, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',20,1,'15:30','16:30','2023-01-09','2023-05-21','SATURDAY',2,'SATURDAY, 2023-01-09, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',20,1,'14:00','15:00','2023-01-09','2023-05-21','SUNDAY',2,'SUNDAY, 2023-01-09, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',20,1,'15:30','16:30','2023-01-09','2023-05-21','SUNDAY',2,'SUNDAY, 2023-01-09, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,'BLI_SUPER_ENHANCED','BLI','GRP1 SUPER ENHANCED','SOCIAL',25,0,'14:00','16:00','2023-05-22','2023-06-09','MONDAY',1,'MONDAY, 2023-05-22, 14:00',true),
                    (NULL,NULL,NULL,'CFI','Visits Main Hall','SOCIAL',40,0,'13:45','15:45','2023-01-23',NULL,'MONDAY',1,'MONDAY, 2023-01-23, 13:45',true),
                    (NULL,NULL,NULL,'CFI','Visits Main Hall','SOCIAL',40,0,'13:45','15:45','2023-01-23',NULL,'TUESDAY',1,'TUESDAY, 2023-01-23, 13:45',true),
                    (NULL,NULL,NULL,'CFI','Visits Main Hall','SOCIAL',0,2,'13:45','14:45','2023-01-23',NULL,'TUESDAY',1,'TUESDAY, 2023-01-23, 13:45',true),
                    (NULL,NULL,'CFI_ENHANCED','CFI','VMH Enhanced','SOCIAL',15,0,'18:00','18:45','2023-01-23',NULL,'TUESDAY',1,'TUESDAY, 2023-01-23, 18:00',true),
                    (NULL,NULL,NULL,'CFI','Visits Main Hall','SOCIAL',40,0,'13:45','15:45','2023-01-23',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-01-23, 13:45',true),
                    (NULL,NULL,NULL,'CFI','Visits Main Hall','SOCIAL',0,2,'13:45','14:45','2023-01-23',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-01-23, 13:45',true),
                    (NULL,NULL,NULL,'CFI','Visits Main Hall','SOCIAL',40,0,'13:45','15:45','2023-01-23',NULL,'THURSDAY',1,'THURSDAY, 2023-01-23, 13:45',true),
                    (NULL,NULL,NULL,'CFI','Visits Main Hall','SOCIAL',0,2,'13:45','14:45','2023-01-23',NULL,'THURSDAY',1,'THURSDAY, 2023-01-23, 13:45',true),
                    (NULL,NULL,NULL,'CFI','Visits Main Hall','SOCIAL',40,0,'13:45','15:45','2023-01-23',NULL,'FRIDAY',1,'FRIDAY, 2023-01-23, 13:45',true),
                    (NULL,NULL,NULL,'CFI','Visits Main Hall','SOCIAL',0,2,'13:45','14:45','2023-01-23',NULL,'FRIDAY',1,'FRIDAY, 2023-01-23, 13:45',true),
                    (NULL,NULL,NULL,'CFI','Visits Main Hall','SOCIAL',40,0,'09:30','11:30','2023-01-23',NULL,'SATURDAY',1,'SATURDAY, 2023-01-23, 09:30',true),
                    (NULL,NULL,NULL,'CFI','Visits Main Hall','SOCIAL',40,0,'13:45','15:45','2023-01-23',NULL,'SATURDAY',1,'SATURDAY, 2023-01-23, 13:45',true),
                    (NULL,NULL,NULL,'CFI','Visits Main Hall','SOCIAL',40,0,'13:45','15:45','2023-01-23',NULL,'SUNDAY',1,'SUNDAY, 2023-01-23, 13:45',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'09:00','10:00','2023-01-23',NULL,'MONDAY',1,'MONDAY, 2023-01-23, 09:00',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'10:30','11:30','2023-01-23',NULL,'MONDAY',1,'MONDAY, 2023-01-23, 10:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'13:30','14:30','2023-01-23',NULL,'MONDAY',1,'MONDAY, 2023-01-23, 13:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'15:30','16:30','2023-01-23',NULL,'MONDAY',1,'MONDAY, 2023-01-23, 15:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'09:00','10:00','2023-01-23',NULL,'TUESDAY',1,'TUESDAY, 2023-01-23, 09:00',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'10:30','11:30','2023-01-23',NULL,'TUESDAY',1,'TUESDAY, 2023-01-23, 10:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'13:30','14:30','2023-01-23',NULL,'TUESDAY',1,'TUESDAY, 2023-01-23, 13:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'15:30','16:30','2023-01-23',NULL,'TUESDAY',1,'TUESDAY, 2023-01-23, 15:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'09:00','10:00','2023-01-23',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-01-23, 09:00',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'10:30','11:30','2023-01-23',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-01-23, 10:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'09:00','10:00','2023-01-23',NULL,'THURSDAY',1,'THURSDAY, 2023-01-23, 09:00',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'10:30','11:30','2023-01-23',NULL,'THURSDAY',1,'THURSDAY, 2023-01-23, 10:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'13:30','14:30','2023-01-23',NULL,'THURSDAY',1,'THURSDAY, 2023-01-23, 13:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'15:30','16:30','2023-01-23',NULL,'THURSDAY',1,'THURSDAY, 2023-01-23, 15:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'09:00','10:00','2023-01-23',NULL,'SATURDAY',1,'SATURDAY, 2023-01-23, 09:00',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'10:30','11:30','2023-01-23',NULL,'SATURDAY',1,'SATURDAY, 2023-01-23, 10:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'13:30','14:30','2023-01-23',NULL,'SATURDAY',1,'SATURDAY, 2023-01-23, 13:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'15:30','16:30','2023-01-23',NULL,'SATURDAY',1,'SATURDAY, 2023-01-23, 15:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'09:00','10:00','2023-01-23',NULL,'SUNDAY',1,'SUNDAY, 2023-01-23, 09:00',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'10:30','11:30','2023-01-23',NULL,'SUNDAY',1,'SUNDAY, 2023-01-23, 10:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'13:30','14:30','2023-01-23',NULL,'SUNDAY',1,'SUNDAY, 2023-01-23, 13:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'15:30','16:30','2023-01-23',NULL,'SUNDAY',1,'SUNDAY, 2023-01-23, 15:30',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'14:00','15:00','2023-01-23',NULL,'MONDAY',1,'MONDAY, 2023-01-23, 14:00',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'15:30','16:30','2023-01-23',NULL,'MONDAY',1,'MONDAY, 2023-01-23, 15:30',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'14:00','15:00','2023-01-23',NULL,'TUESDAY',1,'TUESDAY, 2023-01-23, 14:00',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'15:30','16:30','2023-01-23',NULL,'TUESDAY',1,'TUESDAY, 2023-01-23, 15:30',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'14:00','15:00','2023-01-23',NULL,'THURSDAY',1,'THURSDAY, 2023-01-23, 14:00',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'15:30','16:30','2023-01-23',NULL,'THURSDAY',1,'THURSDAY, 2023-01-23, 15:30',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'14:00','15:00','2023-01-23',NULL,'FRIDAY',1,'FRIDAY, 2023-01-23, 14:00',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'15:30','16:30','2023-01-23',NULL,'FRIDAY',1,'FRIDAY, 2023-01-23, 15:30',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'14:00','15:00','2023-01-23',NULL,'SATURDAY',1,'SATURDAY, 2023-01-23, 14:00',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'15:30','16:30','2023-01-23',NULL,'SATURDAY',1,'SATURDAY, 2023-01-23, 15:30',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'14:00','15:00','2023-01-23',NULL,'SUNDAY',1,'SUNDAY, 2023-01-23, 14:00',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'15:30','16:30','2023-01-23',NULL,'SUNDAY',1,'SUNDAY, 2023-01-23, 15:30',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',10,2,'14:00','15:00','2023-03-10','2023-05-17','TUESDAY',1,'TUESDAY, 2023-03-10, 14:00',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',10,2,'15:30','16:30','2023-03-10','2023-05-17','TUESDAY',1,'TUESDAY, 2023-03-10, 15:30',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',10,2,'14:00','15:00','2023-03-10','2023-05-17','WEDNESDAY',1,'WEDNESDAY, 2023-03-10, 14:00',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',10,2,'15:30','16:30','2023-03-10','2023-05-17','WEDNESDAY',1,'WEDNESDAY, 2023-03-10, 15:30',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',10,2,'14:00','15:00','2023-03-10','2023-05-17','FRIDAY',1,'FRIDAY, 2023-03-10, 14:00',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',10,2,'15:30','16:30','2023-03-10','2023-05-17','FRIDAY',1,'FRIDAY, 2023-03-10, 15:30',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',10,2,'14:00','15:00','2023-03-10','2023-05-17','SATURDAY',1,'SATURDAY, 2023-03-10, 14:00',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',10,2,'15:30','16:30','2023-03-10','2023-05-17','SATURDAY',1,'SATURDAY, 2023-03-10, 15:30',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',10,2,'14:00','15:00','2023-03-10','2023-05-17','SUNDAY',1,'SUNDAY, 2023-03-10, 14:00',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',10,2,'15:30','16:30','2023-03-10','2023-05-17','SUNDAY',1,'SUNDAY, 2023-03-10, 15:30',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',12,2,'14:00','15:00','2023-05-18',NULL,'TUESDAY',1,'TUESDAY, 2023-05-18, 14:00',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',12,2,'15:30','16:30','2023-05-18',NULL,'TUESDAY',1,'TUESDAY, 2023-05-18, 15:30',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',12,2,'14:00','15:00','2023-05-18',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-05-18, 14:00',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',12,2,'15:30','16:30','2023-05-18',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-05-18, 15:30',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',12,2,'14:00','15:00','2023-05-18',NULL,'FRIDAY',1,'FRIDAY, 2023-05-18, 14:00',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',12,2,'15:30','16:30','2023-05-18',NULL,'FRIDAY',1,'FRIDAY, 2023-05-18, 15:30',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',12,2,'14:00','15:00','2023-05-18',NULL,'SATURDAY',1,'SATURDAY, 2023-05-18, 14:00',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',12,2,'15:30','16:30','2023-05-18',NULL,'SATURDAY',1,'SATURDAY, 2023-05-18, 15:30',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',12,2,'14:00','15:00','2023-05-18',NULL,'SUNDAY',1,'SUNDAY, 2023-05-18, 14:00',true),
                    (NULL,NULL,NULL,'EWI','Visits Main Hall','SOCIAL',12,2,'15:30','16:30','2023-05-18',NULL,'SUNDAY',1,'SUNDAY, 2023-05-18, 15:30',true),
                    (NULL,NULL,NULL,'DHI','Visits Main Hall','SOCIAL',24,3,'13:30','15:45','2023-03-10',NULL,'TUESDAY',1,'TUESDAY, 2023-03-10, 13:30',true),
                    (NULL,NULL,NULL,'DHI','Visits Main Hall','SOCIAL',24,3,'09:30','11:30','2023-03-10',NULL,'SATURDAY',1,'SATURDAY, 2023-03-10, 09:30',true),
                    (NULL,NULL,NULL,'DHI','Visits Main Hall','SOCIAL',24,3,'13:30','15:45','2023-03-10',NULL,'SATURDAY',1,'SATURDAY, 2023-03-10, 13:30',true),
                    (NULL,NULL,NULL,'DHI','Visits Main Hall','SOCIAL',24,3,'13:30','15:45','2023-03-10',NULL,'SUNDAY',1,'SUNDAY, 2023-03-10, 13:30',true),
                    (NULL,NULL,NULL,'MHI','Visits Main Hall','SOCIAL',14,2,'13:15','16:00','2023-03-10','2023-07-13','TUESDAY',1,'TUESDAY, 2023-03-10, 13:15',true),
                    (NULL,NULL,NULL,'MHI','Visits Main Hall','SOCIAL',14,2,'13:15','16:00','2023-03-10','2023-04-05','THURSDAY',1,'THURSDAY, 2023-03-10, 13:15',true),
                    (NULL,NULL,NULL,'MHI','Visits Main Hall','SOCIAL',14,2,'13:30','16:15','2023-03-10','2023-07-13','SATURDAY',1,'SATURDAY, 2023-03-10, 13:30',true),
                    (NULL,NULL,NULL,'MHI','Visits Main Hall','SOCIAL',14,2,'13:30','16:15','2023-03-10','2023-07-13','SUNDAY',1,'SUNDAY, 2023-03-10, 13:30',true),
                    (NULL,NULL,NULL,'MHI','Visits Main Hall','SOCIAL',14,2,'13:15','16:00','2023-04-07','2023-07-13','THURSDAY',1,'THURSDAY, 2023-04-07, 13:15',true),
                    (NULL,NULL,NULL,'BNI','Visits Main Hall','SOCIAL',60,5,'14:15','15:45','2023-03-10','2023-04-10','TUESDAY',1,'TUESDAY, 2023-03-10, 14:15',true),
                    (NULL,NULL,NULL,'BNI','Visits Main Hall','SOCIAL',60,5,'14:15','15:45','2023-03-10','2023-04-10','WEDNESDAY',1,'WEDNESDAY, 2023-03-10, 14:15',true),
                    (NULL,NULL,NULL,'BNI','Visits Main Hall','SOCIAL',60,5,'14:15','15:45','2023-03-10','2023-04-10','THURSDAY',1,'THURSDAY, 2023-03-10, 14:15',true),
                    (NULL,NULL,NULL,'BNI','Visits Main Hall','SOCIAL',60,5,'14:15','15:45','2023-03-10','2023-04-10','SATURDAY',1,'SATURDAY, 2023-03-10, 14:15',true),
                    (NULL,NULL,NULL,'BNI','Visits Main Hall','SOCIAL',60,5,'14:15','15:45','2023-03-10','2023-04-10','SUNDAY',1,'SUNDAY, 2023-03-10, 14:15',true),
                    (NULL,NULL,NULL,'BNI','Visits Main Hall','SOCIAL',60,5,'13:45','15:45','2023-04-11','2023-06-14','TUESDAY',1,'TUESDAY, 2023-04-11, 13:45',true),
                    (NULL,NULL,NULL,'BNI','Visits Main Hall','SOCIAL',60,5,'13:45','15:45','2023-04-11','2023-06-14','WEDNESDAY',1,'WEDNESDAY, 2023-04-11, 13:45',true),
                    (NULL,NULL,NULL,'BNI','Visits Main Hall','SOCIAL',60,5,'13:45','15:45','2023-04-11','2023-06-14','THURSDAY',1,'THURSDAY, 2023-04-11, 13:45',true),
                    (NULL,NULL,NULL,'BNI','Visits Main Hall','SOCIAL',60,5,'13:45','15:45','2023-04-11','2023-06-14','SATURDAY',1,'SATURDAY, 2023-04-11, 13:45',true),
                    (NULL,NULL,NULL,'BNI','Visits Main Hall','SOCIAL',60,5,'13:45','15:45','2023-04-11','2023-06-14','SUNDAY',1,'SUNDAY, 2023-04-11, 13:45',true),
                    ('BNI_OTHER_WINGS',NULL,NULL,'BNI','Visits Main Hall','SOCIAL',48,4,'13:45','15:45','2023-06-15',NULL,'TUESDAY',1,'TUESDAY, 2023-06-15, 13:45',true),
                    ('BNI_A_WING',NULL,NULL,'BNI','VMH Wing A','SOCIAL',12,1,'13:45','15:45','2023-06-15',NULL,'TUESDAY',1,'TUESDAY, 2023-06-15, 13:45',true),
                    ('BNI_OTHER_WINGS',NULL,NULL,'BNI','Visits Main Hall','SOCIAL',48,4,'13:45','15:45','2023-06-15',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-06-15, 13:45',true),
                    ('BNI_A_WING',NULL,NULL,'BNI','VMH Wing A','SOCIAL',12,1,'13:45','15:45','2023-06-15',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-06-15, 13:45',true),
                    ('BNI_OTHER_WINGS',NULL,NULL,'BNI','Visits Main Hall','SOCIAL',48,4,'13:45','15:45','2023-06-15',NULL,'THURSDAY',1,'THURSDAY, 2023-06-15, 13:45',true),
                    ('BNI_A_WING',NULL,NULL,'BNI','VMH Wing A','SOCIAL',12,1,'13:45','15:45','2023-06-15',NULL,'THURSDAY',1,'THURSDAY, 2023-06-15, 13:45',true),
                    ('BNI_OTHER_WINGS',NULL,NULL,'BNI','Visits Main Hall','SOCIAL',48,4,'13:45','15:45','2023-06-15',NULL,'SATURDAY',1,'SATURDAY, 2023-06-15, 13:45',true),
                    ('BNI_A_WING',NULL,NULL,'BNI','VMH Wing A','SOCIAL',12,1,'13:45','15:45','2023-06-15',NULL,'SATURDAY',1,'SATURDAY, 2023-06-15, 13:45',true),
                    ('BNI_OTHER_WINGS',NULL,NULL,'BNI','Visits Main Hall','SOCIAL',48,4,'13:45','15:45','2023-06-15',NULL,'SUNDAY',1,'SUNDAY, 2023-06-15, 13:45',true),
                    ('BNI_A_WING',NULL,NULL,'BNI','VMH Wing A','SOCIAL',12,1,'13:45','15:45','2023-06-15',NULL,'SUNDAY',1,'SUNDAY, 2023-06-15, 13:45',true),
                    (NULL,'FNI_NON_CAT_A_HIGH',NULL,'FNI','Visits Main Hall','SOCIAL',30,4,'14:00','16:15','2023-05-18',NULL,'FRIDAY',1,'FRIDAY, 2023-05-18, 14:00',true),
                    (NULL,'FNI_CAT_A_HIGH',NULL,'FNI','VMH Cat A High','SOCIAL',3,0,'14:00','16:00','2023-05-18',NULL,'FRIDAY',1,'FRIDAY, 2023-05-18, 14:00',true),
                    (NULL,'FNI_NON_CAT_A_HIGH',NULL,'FNI','Visits Main Hall','SOCIAL',30,4,'14:00','16:15','2023-05-18',NULL,'SATURDAY',1,'SATURDAY, 2023-05-18, 14:00',true),
                    (NULL,'FNI_CAT_A_HIGH',NULL,'FNI','VMH Cat A High','SOCIAL',3,0,'14:00','16:00','2023-05-18',NULL,'SATURDAY',1,'SATURDAY, 2023-05-18, 14:00',true),
                    (NULL,'FNI_NON_CAT_A_HIGH',NULL,'FNI','Visits Main Hall','SOCIAL',30,4,'14:00','16:15','2023-05-18',NULL,'SUNDAY',1,'SUNDAY, 2023-05-18, 14:00',true),
                    (NULL,'FNI_CAT_A_HIGH',NULL,'FNI','VMH Cat A High','SOCIAL',3,0,'14:00','16:00','2023-05-18',NULL,'SUNDAY',1,'SUNDAY, 2023-05-18, 14:00',true),
                    (NULL,NULL,NULL,'LNI','Visits Main Hall','SOCIAL',12,3,'14:15','16:15','2023-03-21',NULL,'TUESDAY',1,'TUESDAY, 2023-03-21, 14:15',true),
                    (NULL,NULL,NULL,'LNI','Visits Main Hall','SOCIAL',12,3,'14:15','16:15','2023-03-21',NULL,'THURSDAY',1,'THURSDAY, 2023-03-21, 14:15',true),
                    (NULL,NULL,NULL,'LNI','Visits Main Hall','SOCIAL',12,3,'14:00','16:00','2023-03-21',NULL,'SATURDAY',1,'SATURDAY, 2023-03-21, 14:00',true),
                    (NULL,NULL,NULL,'LNI','Visits Main Hall','SOCIAL',12,3,'14:00','16:00','2023-03-21',NULL,'SUNDAY',1,'SUNDAY, 2023-03-21, 14:00',true),
                    (NULL,NULL,NULL,'FHI','Visits Main Hall','SOCIAL',12,2,'14:00','16:00','2023-03-21',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-03-21, 14:00',true),
                    (NULL,NULL,NULL,'FHI','Visits Main Hall','SOCIAL',12,2,'14:00','16:00','2023-03-21',NULL,'FRIDAY',1,'FRIDAY, 2023-03-21, 14:00',true),
                    (NULL,NULL,NULL,'FHI','Visits Main Hall','SOCIAL',12,2,'09:30','11:30','2023-03-21',NULL,'SUNDAY',1,'SUNDAY, 2023-03-21, 09:30',true),
                    (NULL,NULL,NULL,'FHI','Visits Main Hall','SOCIAL',12,2,'14:00','16:00','2023-03-21',NULL,'SUNDAY',1,'SUNDAY, 2023-03-21, 14:00',true),
                    (NULL,NULL,NULL,'ESI','Visits Main Hall','SOCIAL',12,0,'14:00','16:00','2023-03-21',NULL,'SATURDAY',1,'SATURDAY, 2023-03-21, 14:00',true),
                    (NULL,NULL,NULL,'ESI','Visits Main Hall','SOCIAL',12,0,'14:00','16:00','2023-03-21',NULL,'SUNDAY',1,'SUNDAY, 2023-03-21, 14:00',true),
                    (NULL,NULL,NULL,'BSI','Visits Main Hall','SOCIAL',25,6,'14:00','16:00','2023-03-21',NULL,'MONDAY',1,'MONDAY, 2023-03-21, 14:00',true),
                    (NULL,NULL,NULL,'BSI','Visits Main Hall','SOCIAL',25,6,'14:00','16:00','2023-03-21',NULL,'TUESDAY',1,'TUESDAY, 2023-03-21, 14:00',true),
                    (NULL,NULL,NULL,'BSI','Visits Main Hall','SOCIAL',25,6,'14:00','16:00','2023-03-21',NULL,'THURSDAY',1,'THURSDAY, 2023-03-21, 14:00',true),
                    (NULL,NULL,NULL,'BSI','Visits Main Hall','SOCIAL',25,6,'14:00','16:00','2023-03-21',NULL,'SATURDAY',1,'SATURDAY, 2023-03-21, 14:00',true),
                    (NULL,NULL,NULL,'BSI','Visits Main Hall','SOCIAL',25,6,'14:00','16:00','2023-03-21',NULL,'SUNDAY',1,'SUNDAY, 2023-03-21, 14:00',true),
                    (NULL,NULL,NULL,'AGI','Visits Main Hall','SOCIAL',15,0,'14:00','16:00','2023-03-21',NULL,'SATURDAY',1,'SATURDAY, 2023-03-21, 14:00',true),
                    (NULL,NULL,NULL,'AGI','Visits Main Hall','SOCIAL',15,0,'14:00','16:00','2023-03-21',NULL,'SUNDAY',1,'SUNDAY, 2023-03-21, 14:00',true),
                    ('DMI_OTHER_WINGS',NULL,NULL,'DMI','VMH Wings ABDEGIM','SOCIAL',22,0,'13:45','15:45','2023-05-22',NULL,'MONDAY',1,'MONDAY, 2023-05-22, 13:45',true),
                    ('DMI_C_F_WING',NULL,NULL,'DMI','VMH Wings C and F','SOCIAL',5,0,'13:45','15:45','2023-05-22',NULL,'MONDAY',1,'MONDAY, 2023-05-22, 13:45',true),
                    ('DMI_OTHER_WINGS',NULL,NULL,'DMI','VMH Wings ABDEGIM','SOCIAL',22,0,'09:30','11:30','2023-05-22',NULL,'TUESDAY',1,'TUESDAY, 2023-05-22, 09:30',true),
                    ('DMI_C_F_WING',NULL,NULL,'DMI','VMH Wings C and F','SOCIAL',5,0,'09:30','11:30','2023-05-22',NULL,'TUESDAY',1,'TUESDAY, 2023-05-22, 09:30',true),
                    ('DMI_OTHER_WINGS',NULL,NULL,'DMI','VMH Wings ABDEGIM','SOCIAL',22,0,'13:45','15:45','2023-05-22',NULL,'TUESDAY',1,'TUESDAY, 2023-05-22, 13:45',true),
                    ('DMI_C_F_WING',NULL,NULL,'DMI','VMH Wings C and F','SOCIAL',5,0,'13:45','15:45','2023-05-22',NULL,'TUESDAY',1,'TUESDAY, 2023-05-22, 13:45',true),
                    ('DMI_C_F_WING',NULL,NULL,'DMI','VMH Wings C and F','SOCIAL',27,6,'09:30','11:30','2023-05-22','2023-07-09','WEDNESDAY',1,'WEDNESDAY, 2023-05-22, 09:30',true),
                    ('DMI_OTHER_WINGS',NULL,NULL,'DMI','VMH Wings ABDEGIM','SOCIAL',22,0,'13:45','15:45','2023-05-22',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-05-22, 13:45',true),
                    ('DMI_C_F_WING',NULL,NULL,'DMI','VMH Wings C and F','SOCIAL',5,0,'13:45','15:45','2023-05-22',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-05-22, 13:45',true),
                    ('DMI_OTHER_WINGS',NULL,NULL,'DMI','VMH Wings ABDEGIM','SOCIAL',22,0,'13:45','15:45','2023-05-22',NULL,'THURSDAY',1,'THURSDAY, 2023-05-22, 13:45',true),
                    ('DMI_C_F_WING',NULL,NULL,'DMI','VMH Wings C and F','SOCIAL',5,0,'13:45','15:45','2023-05-22',NULL,'THURSDAY',1,'THURSDAY, 2023-05-22, 13:45',true),
                    ('DMI_OTHER_WINGS',NULL,NULL,'DMI','VMH Wings ABDEGIM','SOCIAL',22,0,'13:45','15:45','2023-05-22',NULL,'FRIDAY',1,'FRIDAY, 2023-05-22, 13:45',true),
                    ('DMI_C_F_WING',NULL,NULL,'DMI','VMH Wings C and F','SOCIAL',5,0,'13:45','15:45','2023-05-22',NULL,'FRIDAY',1,'FRIDAY, 2023-05-22, 13:45',true),
                    ('DMI_OTHER_WINGS',NULL,NULL,'DMI','VMH Wings ABDEGIM','SOCIAL',22,0,'09:30','11:30','2023-05-22','2023-07-09','SATURDAY',1,'SATURDAY, 2023-05-22, 09:30',true),
                    ('DMI_C_F_WING',NULL,NULL,'DMI','VMH Wings C and F','SOCIAL',5,0,'09:30','11:30','2023-05-22','2023-07-09','SATURDAY',1,'SATURDAY, 2023-05-22, 09:30',true),
                    ('DMI_OTHER_WINGS',NULL,NULL,'DMI','VMH Wings ABDEGIM','SOCIAL',22,0,'13:45','15:45','2023-05-22',NULL,'SATURDAY',1,'SATURDAY, 2023-05-22, 13:45',true),
                    ('DMI_C_F_WING',NULL,NULL,'DMI','VMH Wings C and F','SOCIAL',5,0,'13:45','15:45','2023-05-22',NULL,'SATURDAY',1,'SATURDAY, 2023-05-22, 13:45',true),
                    ('DMI_OTHER_WINGS',NULL,NULL,'DMI','VMH Wings ABDEGIM','SOCIAL',22,0,'09:30','11:30','2023-05-22',NULL,'SUNDAY',1,'SUNDAY, 2023-05-22, 09:30',true),
                    ('DMI_C_F_WING',NULL,NULL,'DMI','VMH Wings C and F','SOCIAL',5,0,'09:30','11:30','2023-05-22',NULL,'SUNDAY',1,'SUNDAY, 2023-05-22, 09:30',true),
                    ('DMI_OTHER_WINGS',NULL,NULL,'DMI','VMH Wings ABDEGIM','SOCIAL',22,0,'13:45','15:45','2023-05-22',NULL,'SUNDAY',1,'SUNDAY, 2023-05-22, 13:45',true),
                    ('DMI_C_F_WING',NULL,NULL,'DMI','VMH Wings C and F','SOCIAL',5,0,'13:45','15:45','2023-05-22',NULL,'SUNDAY',1,'SUNDAY, 2023-05-22, 13:45',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',25,1,'14:00','15:00','2023-05-22',NULL,'TUESDAY',2,'TUESDAY, 2023-05-22, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',25,1,'15:30','16:30','2023-05-22',NULL,'TUESDAY',2,'TUESDAY, 2023-05-22, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',25,1,'14:00','15:00','2023-05-22',NULL,'WEDNESDAY',2,'WEDNESDAY, 2023-05-22, 14:00',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','WC L2 CC 1TO8 25TO32','SOCIAL',25,1,'15:30','16:30','2023-05-22',NULL,'WEDNESDAY',2,'WEDNESDAY, 2023-05-22, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',25,1,'14:00','16:00','2023-05-22',NULL,'FRIDAY',2,'FRIDAY, 2023-05-22, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',25,1,'14:00','15:00','2023-05-22',NULL,'SATURDAY',2,'SATURDAY, 2023-05-22, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',25,1,'15:30','16:30','2023-05-22',NULL,'SATURDAY',2,'SATURDAY, 2023-05-22, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',25,1,'14:00','15:00','2023-05-22',NULL,'SUNDAY',2,'SUNDAY, 2023-05-22, 14:00',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','WC L2 CC 1TO8 25TO32','SOCIAL',25,1,'15:30','16:30','2023-05-22',NULL,'SUNDAY',2,'SUNDAY, 2023-05-22, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',25,1,'14:00','15:00','2023-05-29',NULL,'TUESDAY',2,'TUESDAY, 2023-05-29, 14:00',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','WC L2 CC 1TO8 25TO32','SOCIAL',25,1,'15:30','16:30','2023-05-29',NULL,'TUESDAY',2,'TUESDAY, 2023-05-29, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',25,1,'14:00','16:00','2023-05-29',NULL,'WEDNESDAY',2,'WEDNESDAY, 2023-05-29, 14:00',true),
                    ('BLI_G2_WING:BLI_G2_C2',NULL,NULL,'BLI','WC L2 CC 1TO8 25TO32','SOCIAL',25,1,'14:00','16:00','2023-05-29',NULL,'FRIDAY',2,'FRIDAY, 2023-05-29, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',25,1,'14:00','15:00','2023-05-29',NULL,'SATURDAY',2,'SATURDAY, 2023-05-29, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',25,1,'15:30','16:30','2023-05-29',NULL,'SATURDAY',2,'SATURDAY, 2023-05-29, 15:30',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',25,1,'14:00','15:00','2023-05-29',NULL,'SUNDAY',2,'SUNDAY, 2023-05-29, 14:00',true),
                    ('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3',NULL,NULL,'BLI','WINGS ABGFEH SOME C','SOCIAL',25,1,'15:30','16:30','2023-05-29',NULL,'SUNDAY',2,'SUNDAY, 2023-05-29, 15:30',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'14:00','15:00','2023-06-02',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-06-02, 14:00',true),
                    (NULL,NULL,NULL,'PNI','Visits Main Hall','SOCIAL',32,8,'15:30','16:30','2023-06-02',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-06-02, 15:30',true),
                    (NULL,NULL,NULL,'DMI','Visits Main Hall','SOCIAL',0,6,'13:45','15:45','2023-06-07',NULL,'MONDAY',1,'MONDAY, 2023-06-07, 13:45',true),
                    (NULL,NULL,NULL,'DMI','Visits Main Hall','SOCIAL',0,6,'09:30','11:30','2023-06-07',NULL,'TUESDAY',1,'TUESDAY, 2023-06-07, 09:30',true),
                    (NULL,NULL,NULL,'DMI','Visits Main Hall','SOCIAL',0,6,'13:45','15:45','2023-06-07',NULL,'TUESDAY',1,'TUESDAY, 2023-06-07, 13:45',true),
                    (NULL,NULL,NULL,'DMI','Visits Main Hall','SOCIAL',0,6,'13:45','15:45','2023-06-07',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-06-07, 13:45',true),
                    (NULL,NULL,NULL,'DMI','Visits Main Hall','SOCIAL',0,6,'13:45','15:45','2023-06-07',NULL,'THURSDAY',1,'THURSDAY, 2023-06-07, 13:45',true),
                    (NULL,NULL,NULL,'DMI','Visits Main Hall','SOCIAL',0,6,'13:45','15:45','2023-06-07',NULL,'FRIDAY',1,'FRIDAY, 2023-06-07, 13:45',true),
                    (NULL,NULL,NULL,'DMI','Visits Main Hall','SOCIAL',0,6,'09:30','11:30','2023-06-07','2023-07-09','SATURDAY',1,'SATURDAY, 2023-06-07, 09:30',true),
                    (NULL,NULL,NULL,'DMI','Visits Main Hall','SOCIAL',0,6,'13:45','15:45','2023-06-07',NULL,'SATURDAY',1,'SATURDAY, 2023-06-07, 13:45',true),
                    (NULL,NULL,NULL,'DMI','Visits Main Hall','SOCIAL',0,6,'09:30','11:30','2023-06-07',NULL,'SUNDAY',1,'SUNDAY, 2023-06-07, 09:30',true),
                    (NULL,NULL,NULL,'DMI','Visits Main Hall','SOCIAL',0,6,'13:45','15:45','2023-06-07',NULL,'SUNDAY',1,'SUNDAY, 2023-06-07, 13:45',true),
                    ('DMI_OTHER_WINGS',NULL,NULL,'DMI','VMH Wings ABDEGIM','SOCIAL',27,6,'09:30','11:30','2023-07-10',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-07-10, 09:30',true),
                    ('DMI_C_F_WING',NULL,NULL,'DMI','VMH Wings C and F','SOCIAL',27,6,'09:30','11:30','2023-07-10',NULL,'SATURDAY',1,'SATURDAY, 2023-07-10, 09:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'13:30','14:30','2023-06-19',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-06-19, 13:30',true),
                    (NULL,NULL,NULL,'WWI','Visits Main Hall','SOCIAL',28,2,'15:30','16:30','2023-06-19',NULL,'WEDNESDAY',1,'WEDNESDAY, 2023-06-19, 15:30',true),
                    (NULL,NULL,NULL,'MHI','Visits Main Hall','SOCIAL',14,2,'14:00','16:00','2023-07-14',NULL,'TUESDAY',1,'TUESDAY, 2023-07-14, 14:00',true),
                    (NULL,NULL,NULL,'MHI','Visits Main Hall','SOCIAL',14,2,'14:00','16:00','2023-07-14',NULL,'THURSDAY',1,'THURSDAY, 2023-07-14, 14:00',true),
                    (NULL,NULL,NULL,'MHI','Visits Main Hall','SOCIAL',14,2,'14:00','16:00','2023-07-14',NULL,'SATURDAY',1,'SATURDAY, 2023-07-14, 14:00',true),
                    (NULL,NULL,NULL,'MHI','Visits Main Hall','SOCIAL',14,2,'14:00','16:00','2023-07-14',NULL,'SUNDAY',1,'SUNDAY, 2023-07-14, 14:00',true)
        ;

        -- update tmp session template table with correct prison id for given code.
        UPDATE tmp_session_template SET prison_id = prison.id FROM prison WHERE tmp_session_template.prison_code = prison.code;

        -- insert data into real session template table from temporary one.
        INSERT INTO session_template(id,reference,visit_room,visit_type,open_capacity,closed_capacity,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id,weekly_frequency,name, active)
        SELECT id,CONCAT('-',REGEXP_REPLACE(to_hex((ROW_NUMBER () OVER (ORDER BY id))+2951597050), '(.{3})(?!$)', '\1.','g')) as reference,visit_room,visit_type,open_capacity,closed_capacity,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id,weekly_frequency,name, active FROM tmp_session_template order by id;

        -- Sequence updated manually as id's were inserted from temp table
        ALTER SEQUENCE session_template_id_seq RESTART WITH  255;

        -- Create temporary group table
        CREATE TABLE tmp_session_location_group (
            id                	serial        NOT NULL PRIMARY KEY,
            prison_code       	VARCHAR(6)    NOT NULL,
            prison_id         	int,
            key          		VARCHAR(50)  NOT NULL,
            name          	    VARCHAR(100)  NOT NULL
        );

        -- Location group names are only descriptions they need to be updated when the group locations change
        INSERT INTO tmp_session_location_group (prison_code,key,name)
        VALUES
            ('BLI','BLI_G1_WING','Wings A, B, G, F, E and H'),
                    ('BLI','BLI_G1_C1','Wing C - Landing 1 - Cells 1 to 32'),
                    ('BLI','BLI_G1_C2','Wing C - Landing 2 - Cells 9 to 24'),
                    ('BLI','BLI_G1_C3','Wing C - Landing 3 - Cells 1 to 24'),
                    ('BLI','BLI_G2_WING','Wings D and F'),
                    ('BLI','BLI_G2_C2','Wing C - Landing 2 - Cells 1 to 8 and 25 to 32'),
                    ('BNI','BNI_A_WING','A Wing'),
                    ('BNI','BNI_OTHER_WINGS','Wings B, C, D, E, F, SSCU and Healthcare'),
                    ('DMI','DMI_C_F_WING','Wings C and F'),
                    ('DMI','DMI_OTHER_WINGS','Wings A, B, D, E, G, I, M'),
                    ('DMI','DMI_F_WING','Wing F')
        ;

        -- update tmp location group table with correct prison id for given code.
        UPDATE tmp_session_location_group SET prison_id = prison.id FROM prison WHERE tmp_session_location_group.prison_code = prison.code;

        -- insert data into real session location group table from temporary one.
        INSERT INTO session_location_group(id,reference,prison_id,name)
                    SELECT id,CONCAT('-',REGEXP_REPLACE(to_hex((ROW_NUMBER () OVER (ORDER BY key))+2951597050), '(.{3})(?!$)', '\1~','g')) as reference,prison_id,name FROM tmp_session_location_group order by id;

        -- Sequence updated manually as id's were inserted from temp table
        ALTER SEQUENCE session_location_group_id_seq RESTART WITH  12;


        -- Create permitted session location data
        CREATE TABLE tmp_permitted_session_location (
            id                serial        NOT NULL PRIMARY KEY,
            group_key        VARCHAR(100)  NOT NULL,
            group_id          int,
            level_one_code    VARCHAR(10) NOT NULL,
            level_two_code    VARCHAR(10),
            level_three_code  VARCHAR(10),
            level_four_code   VARCHAR(10)
        );

        INSERT INTO tmp_permitted_session_location (group_key,level_one_code,level_two_code,level_three_code,level_four_code)
        VALUES
            ('BLI_G1_WING','A',NULL,NULL, NULL),
                    ('BLI_G1_WING','B',NULL,NULL, NULL),
                    ('BLI_G1_WING','G',NULL,NULL, NULL),
                    ('BLI_G1_WING','F',NULL,NULL, NULL),
                    ('BLI_G1_WING','E',NULL,NULL, NULL),
                    ('BLI_G1_WING','H',NULL,NULL, NULL),
                    ('BLI_G1_C1','C','1','001', NULL),
                    ('BLI_G1_C1','C','1','002', NULL),
                    ('BLI_G1_C1','C','1','003', NULL),
                    ('BLI_G1_C1','C','1','004', NULL),
                    ('BLI_G1_C1','C','1','005', NULL),
                    ('BLI_G1_C1','C','1','006', NULL),
                    ('BLI_G1_C1','C','1','007', NULL),
                    ('BLI_G1_C1','C','1','008', NULL),
                    ('BLI_G1_C1','C','1','009', NULL),
                    ('BLI_G1_C1','C','1','010', NULL),
                    ('BLI_G1_C1','C','1','011', NULL),
                    ('BLI_G1_C1','C','1','012', NULL),
                    ('BLI_G1_C1','C','1','013', NULL),
                    ('BLI_G1_C1','C','1','014', NULL),
                    ('BLI_G1_C1','C','1','015', NULL),
                    ('BLI_G1_C1','C','1','016', NULL),
                    ('BLI_G1_C1','C','1','017', NULL),
                    ('BLI_G1_C1','C','1','018', NULL),
                    ('BLI_G1_C1','C','1','019', NULL),
                    ('BLI_G1_C1','C','1','020', NULL),
                    ('BLI_G1_C1','C','1','021', NULL),
                    ('BLI_G1_C1','C','1','022', NULL),
                    ('BLI_G1_C1','C','1','023', NULL),
                    ('BLI_G1_C1','C','1','024', NULL),
                    ('BLI_G1_C1','C','1','025', NULL),
                    ('BLI_G1_C1','C','1','026', NULL),
                    ('BLI_G1_C1','C','1','027', NULL),
                    ('BLI_G1_C1','C','1','028', NULL),
                    ('BLI_G1_C1','C','1','029', NULL),
                    ('BLI_G1_C1','C','1','030', NULL),
                    ('BLI_G1_C1','C','1','031', NULL),
                    ('BLI_G1_C1','C','1','032', NULL),
                    ('BLI_G1_C2','C','2','009', NULL),
                    ('BLI_G1_C2','C','2','010', NULL),
                    ('BLI_G1_C2','C','2','011', NULL),
                    ('BLI_G1_C2','C','2','012', NULL),
                    ('BLI_G1_C2','C','2','013', NULL),
                    ('BLI_G1_C2','C','2','014', NULL),
                    ('BLI_G1_C2','C','2','015', NULL),
                    ('BLI_G1_C2','C','2','016', NULL),
                    ('BLI_G1_C2','C','2','017', NULL),
                    ('BLI_G1_C2','C','2','018', NULL),
                    ('BLI_G1_C2','C','2','019', NULL),
                    ('BLI_G1_C2','C','2','020', NULL),
                    ('BLI_G1_C2','C','2','021', NULL),
                    ('BLI_G1_C2','C','2','022', NULL),
                    ('BLI_G1_C2','C','2','023', NULL),
                    ('BLI_G1_C2','C','2','024', NULL),
                    ('BLI_G1_C3','C','3','001', NULL),
                    ('BLI_G1_C3','C','3','002', NULL),
                    ('BLI_G1_C3','C','3','003', NULL),
                    ('BLI_G1_C3','C','3','004', NULL),
                    ('BLI_G1_C3','C','3','005', NULL),
                    ('BLI_G1_C3','C','3','006', NULL),
                    ('BLI_G1_C3','C','3','007', NULL),
                    ('BLI_G1_C3','C','3','008', NULL),
                    ('BLI_G1_C3','C','3','009', NULL),
                    ('BLI_G1_C3','C','3','010', NULL),
                    ('BLI_G1_C3','C','3','011', NULL),
                    ('BLI_G1_C3','C','3','012', NULL),
                    ('BLI_G1_C3','C','3','013', NULL),
                    ('BLI_G1_C3','C','3','014', NULL),
                    ('BLI_G1_C3','C','3','015', NULL),
                    ('BLI_G1_C3','C','3','016', NULL),
                    ('BLI_G1_C3','C','3','017', NULL),
                    ('BLI_G1_C3','C','3','018', NULL),
                    ('BLI_G1_C3','C','3','019', NULL),
                    ('BLI_G1_C3','C','3','020', NULL),
                    ('BLI_G1_C3','C','3','021', NULL),
                    ('BLI_G1_C3','C','3','022', NULL),
                    ('BLI_G1_C3','C','3','023', NULL),
                    ('BLI_G1_C3','C','3','024', NULL),
                    ('BLI_G2_WING','D',NULL,NULL, NULL),
                    ('BLI_G2_WING','F',NULL,NULL, NULL),
                    ('BLI_G2_C2','C','2','001', NULL),
                    ('BLI_G2_C2','C','2','002', NULL),
                    ('BLI_G2_C2','C','2','003', NULL),
                    ('BLI_G2_C2','C','2','004', NULL),
                    ('BLI_G2_C2','C','2','005', NULL),
                    ('BLI_G2_C2','C','2','006', NULL),
                    ('BLI_G2_C2','C','2','007', NULL),
                    ('BLI_G2_C2','C','2','008', NULL),
                    ('BLI_G2_C2','C','2','025', NULL),
                    ('BLI_G2_C2','C','2','026', NULL),
                    ('BLI_G2_C2','C','2','027', NULL),
                    ('BLI_G2_C2','C','2','028', NULL),
                    ('BLI_G2_C2','C','2','029', NULL),
                    ('BLI_G2_C2','C','2','030', NULL),
                    ('BLI_G2_C2','C','2','031', NULL),
                    ('BLI_G2_C2','C','2','032', NULL),
                    ('BNI_A_WING','A',NULL,NULL, NULL),
                    ('BNI_OTHER_WINGS','B',NULL,NULL, NULL),
                    ('BNI_OTHER_WINGS','C',NULL,NULL, NULL),
                    ('BNI_OTHER_WINGS','D',NULL,NULL, NULL),
                    ('BNI_OTHER_WINGS','E',NULL,NULL, NULL),
                    ('BNI_OTHER_WINGS','F',NULL,NULL, NULL),
                    ('BNI_OTHER_WINGS','S',NULL,NULL, NULL),
                    ('BNI_OTHER_WINGS','H',NULL,NULL, NULL),
                    ('DMI_C_F_WING','C',NULL,NULL, NULL),
                    ('DMI_C_F_WING','F',NULL,NULL, NULL),
                    ('DMI_OTHER_WINGS','A',NULL,NULL, NULL),
                    ('DMI_OTHER_WINGS','B',NULL,NULL, NULL),
                    ('DMI_OTHER_WINGS','D',NULL,NULL, NULL),
                    ('DMI_OTHER_WINGS','E',NULL,NULL, NULL),
                    ('DMI_OTHER_WINGS','G',NULL,NULL, NULL),
                    ('DMI_OTHER_WINGS','I',NULL,NULL, NULL),
                    ('DMI_OTHER_WINGS','M',NULL,NULL, NULL),
                    ('DMI_F_WING','F',NULL,NULL, NULL)
        ;

        -- update tmp location table with correct group_id.
        UPDATE tmp_permitted_session_location SET group_id = tmp_session_location_group.id FROM tmp_session_location_group WHERE tmp_permitted_session_location.group_key = tmp_session_location_group.key;


        -- insert data into real permitted session location table from temporary one.
        INSERT INTO permitted_session_location(id,group_id,level_one_code,level_two_code,level_three_code,level_four_code)
            SELECT id,group_id,level_one_code,level_two_code,level_three_code,level_four_code FROM tmp_permitted_session_location order by id;

        ALTER SEQUENCE permitted_session_location_id_seq RESTART WITH 115;


        -- Create link table data
        INSERT INTO session_to_location_group(session_template_id, group_id)
        SELECT st.id, g.id FROM tmp_session_template st
                                           JOIN tmp_session_location_group g ON POSITION(g.key  IN st.locationKeys)<>0 ORDER BY st.id,g.id;

        -- Location group names are only descriptions they need to be updated when the group locations change

        -- Prisoner category

    -- Create temporary group table
    CREATE TABLE tmp_session_category_group (
    id                	serial        NOT NULL PRIMARY KEY,
    prison_code       	VARCHAR(6)    NOT NULL,
    prison_id         	int,
    key          		VARCHAR(50)  NOT NULL,
    name          	    VARCHAR(100)  NOT NULL
    );

    -- Category group names are only descriptions they need to be updated when the group categories change
        INSERT INTO tmp_session_category_group (prison_code,key,name)
        VALUES
            ('FNI','FNI_CAT_A_HIGH','Category A (High Risk) prisoners'),
                    ('FNI','FNI_NON_CAT_A_HIGH','Non Category A (High Risk) prisoners')
        ;

    -- update tmp category group table with correct prison id for given code.
    UPDATE tmp_session_category_group SET prison_id = prison.id FROM prison WHERE tmp_session_category_group.prison_code = prison.code;

    -- insert data into real session category group table from temporary one.
    INSERT INTO session_category_group(id,reference,prison_id,name)
    SELECT id,CONCAT('-',REGEXP_REPLACE(to_hex((ROW_NUMBER () OVER (ORDER BY key))+2951597050), '(.{3})(?!$)', '\1~','g')) as reference,prison_id,name FROM tmp_session_category_group order by id;

    -- Sequence updated manually as id's were inserted from temp table
    ALTER SEQUENCE session_category_group_id_seq RESTART WITH  3;


    -- Create session prisoner category data
    CREATE TABLE tmp_session_prisoner_category (
    id                serial        NOT NULL PRIMARY KEY,
    group_key        VARCHAR(100)  NOT NULL,
    group_id          int,
    code    VARCHAR(100) NOT NULL
    );

        INSERT INTO tmp_session_prisoner_category (group_key,code)
        VALUES
            ('FNI_CAT_A_HIGH','A_HIGH'),
                    ('FNI_NON_CAT_A_HIGH','A_EXCEPTIONAL'),
                    ('FNI_NON_CAT_A_HIGH','A_PROVISIONAL'),
                    ('FNI_NON_CAT_A_HIGH','A_STANDARD'),
                    ('FNI_NON_CAT_A_HIGH','B'),
                    ('FNI_NON_CAT_A_HIGH','C'),
                    ('FNI_NON_CAT_A_HIGH','D'),
                    ('FNI_NON_CAT_A_HIGH','YOI_CLOSED'),
                    ('FNI_NON_CAT_A_HIGH','YOI_OPEN'),
                    ('FNI_NON_CAT_A_HIGH','YOI_RESTRICTED'),
                    ('FNI_NON_CAT_A_HIGH','UNSENTENCED'),
                    ('FNI_NON_CAT_A_HIGH','UNCATEGORISED_SENTENCED_MALE'),
                    ('FNI_NON_CAT_A_HIGH','FEMALE_RESTRICTED'),
                    ('FNI_NON_CAT_A_HIGH','FEMALE_CLOSED'),
                    ('FNI_NON_CAT_A_HIGH','FEMALE_SEMI'),
                    ('FNI_NON_CAT_A_HIGH','FEMALE_OPEN')
        ;

    -- update tmp prisoner category table with correct group_id.
    UPDATE tmp_session_prisoner_category SET group_id = tmp_session_category_group.id FROM tmp_session_category_group WHERE tmp_session_prisoner_category.group_key = tmp_session_category_group.key;


    -- insert data into real prisoner category table from temporary one.
    INSERT INTO session_prisoner_category(id,session_category_group_id,code)
    SELECT id,group_id,code FROM tmp_session_prisoner_category order by id;

    ALTER SEQUENCE session_prisoner_category_id_seq RESTART WITH 17;


    -- Create link table data
    INSERT INTO session_to_category_group(session_template_id, session_category_group_id)
    SELECT st.id, g.id FROM tmp_session_template st
    JOIN tmp_session_category_group g ON POSITION(g.key  IN st.categoryKeys)<>0 ORDER BY st.id,g.id;

    -- Category group names are only descriptions they need to be updated when the group categories change

    -- Prisoner incentive level

    -- Create temporary group table
    CREATE TABLE tmp_session_incentive_group (
    id                	serial        NOT NULL PRIMARY KEY,
    prison_code       	VARCHAR(6)    NOT NULL,
    prison_id         	int,
    key          		VARCHAR(50)  NOT NULL,
    name          	    VARCHAR(100)  NOT NULL
    );

    -- Incentive group names are only descriptions they need to be updated when the group incentives change
        INSERT INTO tmp_session_incentive_group (prison_code,key,name)
        VALUES
            ('CFI','CFI_ENHANCED','Enhanced prisoners'),
                    ('BLI','BLI_SUPER_ENHANCED','Super Enhanced prisoners')
        ;

    -- update tmp incentive group table with correct prison id for given code.
    UPDATE tmp_session_incentive_group SET prison_id = prison.id FROM prison WHERE tmp_session_incentive_group.prison_code = prison.code;

    -- insert data into real session incentive group table from temporary one.
    INSERT INTO session_incentive_group(id,reference,prison_id,name)
    SELECT id,CONCAT('-',REGEXP_REPLACE(to_hex((ROW_NUMBER () OVER (ORDER BY key))+2951597050), '(.{3})(?!$)', '\1~','g')) as reference,prison_id,name FROM tmp_session_incentive_group order by id;

    -- Sequence updated manually as id's were inserted from temp table
    ALTER SEQUENCE session_incentive_group_id_seq RESTART WITH  3;


    -- Create session prisoner incentive data
    CREATE TABLE tmp_session_prisoner_incentive (
    id                serial        NOT NULL PRIMARY KEY,
    group_key        VARCHAR(100)  NOT NULL,
    group_id          int,
    code    VARCHAR(100) NOT NULL
    );

        INSERT INTO tmp_session_prisoner_incentive (group_key,code)
        VALUES
            ('CFI_ENHANCED','ENHANCED'),
                    ('BLI_SUPER_ENHANCED','ENHANCED_2')
        ;

    -- update tmp prisoner incentive table with correct group_id.
    UPDATE tmp_session_prisoner_incentive SET group_id = tmp_session_incentive_group.id FROM tmp_session_incentive_group WHERE tmp_session_prisoner_incentive.group_key = tmp_session_incentive_group.key;


    -- insert data into real prisoner incentive table from temporary one.
    INSERT INTO session_prisoner_incentive(id,session_incentive_group_id,code)
    SELECT id,group_id,code FROM tmp_session_prisoner_incentive order by id;

    ALTER SEQUENCE session_prisoner_incentive_id_seq RESTART WITH 3;


    -- Create link table data
    INSERT INTO session_to_incentive_group(session_template_id, session_incentive_group_id)
    SELECT st.id, g.id FROM tmp_session_template st
    JOIN tmp_session_incentive_group g ON POSITION(g.key  IN st.incentiveLevelKeys)<>0 ORDER BY st.id,g.id;

    -- Category group names are only descriptions they need to be updated when the group categories change

    INSERT INTO prison_user_client (prison_id,user_type, active) SELECT id,'STAFF',true FROM prison;
    INSERT INTO prison_user_client (prison_id,user_type, active) SELECT id,'PUBLIC',true FROM prison;

    -- Drop temporary tables
        DROP TABLE tmp_session_template;
        DROP TABLE tmp_session_location_group;
        DROP TABLE tmp_permitted_session_location;
        DROP TABLE tmp_session_category_group;
        DROP TABLE tmp_session_prisoner_category;
        DROP TABLE tmp_session_incentive_group;
        DROP TABLE tmp_session_prisoner_incentive;
    END;
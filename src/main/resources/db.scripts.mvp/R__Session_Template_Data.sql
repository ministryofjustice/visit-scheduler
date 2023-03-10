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
	TRUNCATE TABLE session_template  RESTART IDENTITY CASCADE;
	TRUNCATE TABLE permitted_session_location  RESTART IDENTITY CASCADE;

	INSERT INTO prison(code, active) SELECT 'HEI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'HEI');
	INSERT INTO prison(code, active) SELECT 'BLI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'BLI');
	INSERT INTO prison(code, active) SELECT 'CFI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'CFI');
	INSERT INTO prison(code, active) SELECT 'WWI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'WWI');
	INSERT INTO prison(code, active) SELECT 'PNI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'PNI');
	INSERT INTO prison(code, active) SELECT 'EWI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'EWI');
	INSERT INTO prison(code, active) SELECT 'DHI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'DHI');
	INSERT INTO prison(code, active) SELECT 'MHI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'MHI');
	INSERT INTO prison(code, active) SELECT 'BNI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'BNI');

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
		 bi_weekly         BOOLEAN       NOT NULL,
         name              varchar(100)  NOT NULL
		);

	INSERT INTO tmp_session_template (locationKeys,prison_code, visit_room, visit_type, open_capacity, closed_capacity,enhanced, start_time, end_time, valid_from_date, valid_to_date, day_of_week,bi_weekly,name)
	VALUES
		(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2022-05-30','2022-12-18','MONDAY',false,'MONDAY, 2022-05-30, 13:45'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2022-06-01','2022-12-18','WEDNESDAY',false,'WEDNESDAY, 2022-06-01, 13:45'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'09:00','10:00','2022-06-03','2022-12-18','FRIDAY',false,'FRIDAY, 2022-06-03, 09:00'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2022-06-04','2022-12-18','SATURDAY',false,'SATURDAY, 2022-06-04, 13:45'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2022-06-05','2022-12-18','SUNDAY',false,'SUNDAY, 2022-06-05, 13:45'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2022-12-19','2022-12-24','MONDAY',false,'MONDAY, 2022-12-19, 14:00'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2022-12-21','2022-12-24','WEDNESDAY',false,'WEDNESDAY, 2022-12-21, 14:00'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'09:00','11:00','2022-12-23','2022-12-24','FRIDAY',false,'FRIDAY, 2022-12-23, 09:00'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2022-12-24','2022-12-24','SATURDAY',false,'SATURDAY, 2022-12-24, 14:00'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2022-12-28','2023-01-02','WEDNESDAY',false,'WEDNESDAY, 2022-12-28, 14:00'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'09:00','11:00','2022-12-30','2023-01-02','FRIDAY',false,'FRIDAY, 2022-12-30, 09:00'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2022-12-31','2023-01-02','SATURDAY',false,'SATURDAY, 2022-12-31, 14:00'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2023-01-01','2023-01-02','SUNDAY',false,'SUNDAY, 2023-01-01, 14:00'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'14:00','16:00','2023-01-02','2023-01-02','MONDAY',false,'MONDAY, 2023-01-02, 14:00'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2023-01-09',NULL,'MONDAY',false,'MONDAY, 2023-01-09, 13:45'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2023-01-04',NULL,'WEDNESDAY',false,'WEDNESDAY, 2023-01-04, 13:45'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'09:00','10:00','2023-01-06',NULL,'FRIDAY',false,'FRIDAY, 2023-01-06, 09:00'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2023-01-07',NULL,'SATURDAY',false,'SATURDAY, 2023-01-07, 13:45'),
			(NULL,'HEI','Visits Main Room','SOCIAL',30,2,false,'13:45','14:45','2023-01-08',NULL,'SUNDAY',false,'SUNDAY, 2023-01-08, 13:45'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-05','2022-12-18','TUESDAY',true,'TUESDAY, 2022-12-05, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-05','2022-12-18','TUESDAY',true,'TUESDAY, 2022-12-05, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-05','2022-12-18','WEDNESDAY',true,'WEDNESDAY, 2022-12-05, 14:00'),
			('BLI_G2_WING:BLI_G2_C2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-05','2022-12-18','WEDNESDAY',true,'WEDNESDAY, 2022-12-05, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2022-12-05','2022-12-18','FRIDAY',true,'FRIDAY, 2022-12-05, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-05','2022-12-18','SATURDAY',true,'SATURDAY, 2022-12-05, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-05','2022-12-18','SATURDAY',true,'SATURDAY, 2022-12-05, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-05','2022-12-18','SUNDAY',true,'SUNDAY, 2022-12-05, 14:00'),
			('BLI_G2_WING:BLI_G2_C2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-05','2022-12-18','SUNDAY',true,'SUNDAY, 2022-12-05, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-11-28','2022-12-18','TUESDAY',true,'TUESDAY, 2022-11-28, 14:00'),
			('BLI_G2_WING:BLI_G2_C2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-11-28','2022-12-18','TUESDAY',true,'TUESDAY, 2022-11-28, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2022-11-28','2022-12-18','WEDNESDAY',true,'WEDNESDAY, 2022-11-28, 14:00'),
			('BLI_G2_WING:BLI_G2_C2','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2022-11-28','2022-12-18','FRIDAY',true,'FRIDAY, 2022-11-28, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-11-28','2022-12-18','SATURDAY',true,'SATURDAY, 2022-11-28, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-11-28','2022-12-18','SATURDAY',true,'SATURDAY, 2022-11-28, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-11-28','2022-12-18','SUNDAY',true,'SUNDAY, 2022-11-28, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-11-28','2022-12-18','SUNDAY',true,'SUNDAY, 2022-11-28, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-20','2022-12-20','TUESDAY',false,'TUESDAY, 2022-12-20, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-20','2022-12-20','TUESDAY',false,'TUESDAY, 2022-12-20, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-21','2022-12-21','WEDNESDAY',false,'WEDNESDAY, 2022-12-21, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-21','2022-12-21','WEDNESDAY',false,'WEDNESDAY, 2022-12-21, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2022-12-23','2022-12-23','FRIDAY',false,'FRIDAY, 2022-12-23, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-24','2022-12-24','SATURDAY',false,'SATURDAY, 2022-12-24, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-24','2022-12-24','SATURDAY',false,'SATURDAY, 2022-12-24, 15:30'),
			('BLI_G2_WING:BLI_G2_C2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2022-12-27','2022-12-27','TUESDAY',false,'TUESDAY, 2022-12-27, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2022-12-28','2022-12-28','WEDNESDAY',false,'WEDNESDAY, 2022-12-28, 14:00'),
			('BLI_G2_WING:BLI_G2_C2','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2022-12-30','2022-12-30','FRIDAY',false,'FRIDAY, 2022-12-30, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'09:30','10:30','2022-12-31','2022-12-31','SATURDAY',false,'SATURDAY, 2022-12-31, 09:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2022-12-31','2022-12-31','SATURDAY',false,'SATURDAY, 2022-12-31, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-01','2023-01-01','SUNDAY',false,'SUNDAY, 2023-01-01, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-01','2023-01-01','SUNDAY',false,'SUNDAY, 2023-01-01, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-03','2023-01-03','TUESDAY',false,'TUESDAY, 2023-01-03, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-03','2023-01-03','TUESDAY',false,'TUESDAY, 2023-01-03, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-04',NULL,'TUESDAY',true,'TUESDAY, 2023-01-04, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-04',NULL,'TUESDAY',true,'TUESDAY, 2023-01-04, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-04',NULL,'WEDNESDAY',true,'WEDNESDAY, 2023-01-04, 14:00'),
			('BLI_G2_WING:BLI_G2_C2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-04',NULL,'WEDNESDAY',true,'WEDNESDAY, 2023-01-04, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2023-01-04',NULL,'FRIDAY',true,'FRIDAY, 2023-01-04, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-04',NULL,'SATURDAY',true,'SATURDAY, 2023-01-04, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-04',NULL,'SATURDAY',true,'SATURDAY, 2023-01-04, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-04',NULL,'SUNDAY',true,'SUNDAY, 2023-01-04, 14:00'),
			('BLI_G2_WING:BLI_G2_C2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-04',NULL,'SUNDAY',true,'SUNDAY, 2023-01-04, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-09',NULL,'TUESDAY',true,'TUESDAY, 2023-01-09, 14:00'),
			('BLI_G2_WING:BLI_G2_C2','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-09',NULL,'TUESDAY',true,'TUESDAY, 2023-01-09, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2023-01-09',NULL,'WEDNESDAY',true,'WEDNESDAY, 2023-01-09, 14:00'),
			('BLI_G2_WING:BLI_G2_C2','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','16:00','2023-01-09',NULL,'FRIDAY',true,'FRIDAY, 2023-01-09, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-09',NULL,'SATURDAY',true,'SATURDAY, 2023-01-09, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-09',NULL,'SATURDAY',true,'SATURDAY, 2023-01-09, 15:30'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'14:00','15:00','2023-01-09',NULL,'SUNDAY',true,'SUNDAY, 2023-01-09, 14:00'),
			('BLI_G1_WING:BLI_G1_C1:BLI_G1_C2:BLI_G1_C3','BLI','Visits Main Hall','SOCIAL',20,1,false,'15:30','16:30','2023-01-09',NULL,'SUNDAY',true,'SUNDAY, 2023-01-09, 15:30'),
			(NULL,'CFI','Visits Main Hall','SOCIAL',40,0,false,'13:45','15:45','2023-01-23',NULL,'MONDAY',false,'MONDAY, 2023-01-23, 13:45'),
			(NULL,'CFI','Visits Main Hall','SOCIAL',40,0,false,'13:45','15:45','2023-01-23',NULL,'TUESDAY',false,'TUESDAY, 2023-01-23, 13:45'),
			(NULL,'CFI','Visits Main Hall','SOCIAL',0,2,false,'13:45','14:45','2023-01-23',NULL,'TUESDAY',false,'TUESDAY, 2023-01-23, 13:45'),
			(NULL,'CFI','Visits Main Hall','SOCIAL',15,0,true,'18:00','18:45','2023-01-23',NULL,'TUESDAY',false,'TUESDAY, 2023-01-23, 18:00'),
			(NULL,'CFI','Visits Main Hall','SOCIAL',40,0,false,'13:45','15:45','2023-01-23',NULL,'WEDNESDAY',false,'WEDNESDAY, 2023-01-23, 13:45'),
			(NULL,'CFI','Visits Main Hall','SOCIAL',0,2,false,'13:45','14:45','2023-01-23',NULL,'WEDNESDAY',false,'WEDNESDAY, 2023-01-23, 13:45'),
			(NULL,'CFI','Visits Main Hall','SOCIAL',40,0,false,'13:45','15:45','2023-01-23',NULL,'THURSDAY',false,'THURSDAY, 2023-01-23, 13:45'),
			(NULL,'CFI','Visits Main Hall','SOCIAL',0,2,false,'13:45','14:45','2023-01-23',NULL,'THURSDAY',false,'THURSDAY, 2023-01-23, 13:45'),
			(NULL,'CFI','Visits Main Hall','SOCIAL',40,0,false,'13:45','15:45','2023-01-23',NULL,'FRIDAY',false,'FRIDAY, 2023-01-23, 13:45'),
			(NULL,'CFI','Visits Main Hall','SOCIAL',0,2,false,'13:45','14:45','2023-01-23',NULL,'FRIDAY',false,'FRIDAY, 2023-01-23, 13:45'),
			(NULL,'CFI','Visits Main Hall','SOCIAL',40,0,false,'09:30','11:30','2023-01-23',NULL,'SATURDAY',false,'SATURDAY, 2023-01-23, 09:30'),
			(NULL,'CFI','Visits Main Hall','SOCIAL',40,0,false,'13:45','15:45','2023-01-23',NULL,'SATURDAY',false,'SATURDAY, 2023-01-23, 13:45'),
			(NULL,'CFI','Visits Main Hall','SOCIAL',40,0,false,'13:45','15:45','2023-01-23',NULL,'SUNDAY',false,'SUNDAY, 2023-01-23, 13:45'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'09:00','10:00','2023-01-23',NULL,'MONDAY',false,'MONDAY, 2023-01-23, 09:00'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'10:30','11:30','2023-01-23',NULL,'MONDAY',false,'MONDAY, 2023-01-23, 10:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'13:30','14:30','2023-01-23',NULL,'MONDAY',false,'MONDAY, 2023-01-23, 13:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'15:30','16:30','2023-01-23',NULL,'MONDAY',false,'MONDAY, 2023-01-23, 15:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'09:00','10:00','2023-01-23',NULL,'TUESDAY',false,'TUESDAY, 2023-01-23, 09:00'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'10:30','11:30','2023-01-23',NULL,'TUESDAY',false,'TUESDAY, 2023-01-23, 10:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'13:30','14:30','2023-01-23',NULL,'TUESDAY',false,'TUESDAY, 2023-01-23, 13:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'15:30','16:30','2023-01-23',NULL,'TUESDAY',false,'TUESDAY, 2023-01-23, 15:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'09:00','10:00','2023-01-23',NULL,'WEDNESDAY',false,'WEDNESDAY, 2023-01-23, 09:00'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'10:30','11:30','2023-01-23',NULL,'WEDNESDAY',false,'WEDNESDAY, 2023-01-23, 10:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'09:00','10:00','2023-01-23',NULL,'THURSDAY',false,'THURSDAY, 2023-01-23, 09:00'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'10:30','11:30','2023-01-23',NULL,'THURSDAY',false,'THURSDAY, 2023-01-23, 10:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'13:30','14:30','2023-01-23',NULL,'THURSDAY',false,'THURSDAY, 2023-01-23, 13:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'15:30','16:30','2023-01-23',NULL,'THURSDAY',false,'THURSDAY, 2023-01-23, 15:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'09:00','10:00','2023-01-23',NULL,'SATURDAY',false,'SATURDAY, 2023-01-23, 09:00'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'10:30','11:30','2023-01-23',NULL,'SATURDAY',false,'SATURDAY, 2023-01-23, 10:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'13:30','14:30','2023-01-23',NULL,'SATURDAY',false,'SATURDAY, 2023-01-23, 13:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'15:30','16:30','2023-01-23',NULL,'SATURDAY',false,'SATURDAY, 2023-01-23, 15:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'09:00','10:00','2023-01-23',NULL,'SUNDAY',false,'SUNDAY, 2023-01-23, 09:00'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'10:30','11:30','2023-01-23',NULL,'SUNDAY',false,'SUNDAY, 2023-01-23, 10:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'13:30','14:30','2023-01-23',NULL,'SUNDAY',false,'SUNDAY, 2023-01-23, 13:30'),
			(NULL,'WWI','Visits Main Hall','SOCIAL',28,2,false,'15:30','16:30','2023-01-23',NULL,'SUNDAY',false,'SUNDAY, 2023-01-23, 15:30'),
			(NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-01-23',NULL,'MONDAY',false,'MONDAY, 2023-01-23, 14:00'),
			(NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-01-23',NULL,'MONDAY',false,'MONDAY, 2023-01-23, 15:30'),
			(NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-01-23',NULL,'TUESDAY',false,'TUESDAY, 2023-01-23, 14:00'),
			(NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-01-23',NULL,'TUESDAY',false,'TUESDAY, 2023-01-23, 15:30'),
			(NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-01-23',NULL,'THURSDAY',false,'THURSDAY, 2023-01-23, 14:00'),
			(NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-01-23',NULL,'THURSDAY',false,'THURSDAY, 2023-01-23, 15:30'),
			(NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-01-23',NULL,'FRIDAY',false,'FRIDAY, 2023-01-23, 14:00'),
			(NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-01-23',NULL,'FRIDAY',false,'FRIDAY, 2023-01-23, 15:30'),
			(NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-01-23',NULL,'SATURDAY',false,'SATURDAY, 2023-01-23, 14:00'),
			(NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-01-23',NULL,'SATURDAY',false,'SATURDAY, 2023-01-23, 15:30'),
			(NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'14:00','15:00','2023-01-23',NULL,'SUNDAY',false,'SUNDAY, 2023-01-23, 14:00'),
			(NULL,'PNI','Visits Main Hall','SOCIAL',32,8,false,'15:30','16:30','2023-01-23',NULL,'SUNDAY',false,'SUNDAY, 2023-01-23, 15:30'),
			(NULL,'EWI','Visits Main Hall','SOCIAL',10,2,false,'14:00','15:00','2023-03-10',NULL,'TUESDAY',false,'TUESDAY, 2023-03-10, 14:00'),
			(NULL,'EWI','Visits Main Hall','SOCIAL',10,2,false,'15:30','16:30','2023-03-10',NULL,'TUESDAY',false,'TUESDAY, 2023-03-10, 15:30'),
			(NULL,'EWI','Visits Main Hall','SOCIAL',10,2,false,'14:00','15:00','2023-03-10',NULL,'WEDNESDAY',false,'WEDNESDAY, 2023-03-10, 14:00'),
			(NULL,'EWI','Visits Main Hall','SOCIAL',10,2,false,'15:30','16:30','2023-03-10',NULL,'WEDNESDAY',false,'WEDNESDAY, 2023-03-10, 15:30'),
			(NULL,'EWI','Visits Main Hall','SOCIAL',10,2,false,'14:00','15:00','2023-03-10',NULL,'FRIDAY',false,'FRIDAY, 2023-03-10, 14:00'),
			(NULL,'EWI','Visits Main Hall','SOCIAL',10,2,false,'15:30','16:30','2023-03-10',NULL,'FRIDAY',false,'FRIDAY, 2023-03-10, 15:30'),
			(NULL,'EWI','Visits Main Hall','SOCIAL',10,2,false,'14:00','15:00','2023-03-10',NULL,'SATURDAY',false,'SATURDAY, 2023-03-10, 14:00'),
			(NULL,'EWI','Visits Main Hall','SOCIAL',10,2,false,'15:30','16:30','2023-03-10',NULL,'SATURDAY',false,'SATURDAY, 2023-03-10, 15:30'),
			(NULL,'EWI','Visits Main Hall','SOCIAL',10,2,false,'14:00','15:00','2023-03-10',NULL,'SUNDAY',false,'SUNDAY, 2023-03-10, 14:00'),
			(NULL,'EWI','Visits Main Hall','SOCIAL',10,2,false,'15:30','16:30','2023-03-10',NULL,'SUNDAY',false,'SUNDAY, 2023-03-10, 15:30'),
			(NULL,'DHI','Visits Main Hall','SOCIAL',24,3,false,'13:30','15:45','2023-03-10',NULL,'TUESDAY',false,'TUESDAY, 2023-03-10, 13:30'),
			(NULL,'DHI','Visits Main Hall','SOCIAL',24,3,false,'09:30','11:30','2023-03-10',NULL,'SATURDAY',false,'SATURDAY, 2023-03-10, 09:30'),
			(NULL,'DHI','Visits Main Hall','SOCIAL',24,3,false,'13:30','15:45','2023-03-10',NULL,'SATURDAY',false,'SATURDAY, 2023-03-10, 13:30'),
			(NULL,'DHI','Visits Main Hall','SOCIAL',24,3,false,'13:30','15:45','2023-03-10',NULL,'SUNDAY',false,'SUNDAY, 2023-03-10, 13:30'),
			(NULL,'MHI','Visits Main Hall','SOCIAL',14,2,false,'13:15','16:00','2023-03-10',NULL,'TUESDAY',false,'TUESDAY, 2023-03-10, 13:15'),
			(NULL,'MHI','Visits Main Hall','SOCIAL',14,2,false,'13:15','16:00','2023-03-10',NULL,'THURSDAY',false,'THURSDAY, 2023-03-10, 13:15'),
			(NULL,'MHI','Visits Main Hall','SOCIAL',14,2,false,'13:30','16:15','2023-03-10',NULL,'SATURDAY',false,'SATURDAY, 2023-03-10, 13:30'),
			(NULL,'MHI','Visits Main Hall','SOCIAL',14,2,false,'13:30','16:15','2023-03-10',NULL,'SUNDAY',false,'SUNDAY, 2023-03-10, 13:30'),
			(NULL,'BNI','Visits Main Hall','SOCIAL',57,5,false,'14:15','15:45','2023-03-10',NULL,'TUESDAY',false,'TUESDAY, 2023-03-10, 14:15'),
			(NULL,'BNI','Visits Main Hall','SOCIAL',3,0,true,'14:15','15:45','2023-03-10',NULL,'TUESDAY',false,'TUESDAY, 2023-03-10, 14:15'),
			(NULL,'BNI','Visits Main Hall','SOCIAL',57,5,false,'14:15','15:45','2023-03-10',NULL,'WEDNESDAY',false,'WEDNESDAY, 2023-03-10, 14:15'),
			(NULL,'BNI','Visits Main Hall','SOCIAL',3,0,true,'14:15','15:45','2023-03-10',NULL,'WEDNESDAY',false,'WEDNESDAY, 2023-03-10, 14:15'),
			(NULL,'BNI','Visits Main Hall','SOCIAL',57,5,false,'14:15','15:45','2023-03-10',NULL,'THURSDAY',false,'THURSDAY, 2023-03-10, 14:15'),
			(NULL,'BNI','Visits Main Hall','SOCIAL',3,0,true,'14:15','15:45','2023-03-10',NULL,'THURSDAY',false,'THURSDAY, 2023-03-10, 14:15'),
			(NULL,'BNI','Visits Main Hall','SOCIAL',57,5,false,'14:15','15:45','2023-03-10',NULL,'SATURDAY',false,'SATURDAY, 2023-03-10, 14:15'),
			(NULL,'BNI','Visits Main Hall','SOCIAL',3,0,true,'14:15','15:45','2023-03-10',NULL,'SATURDAY',false,'SATURDAY, 2023-03-10, 14:15'),
			(NULL,'BNI','Visits Main Hall','SOCIAL',57,5,false,'14:15','15:45','2023-03-10',NULL,'SUNDAY',false,'SUNDAY, 2023-03-10, 14:15'),
			(NULL,'BNI','Visits Main Hall','SOCIAL',3,0,true,'14:15','15:45','2023-03-10',NULL,'SUNDAY',false,'SUNDAY, 2023-03-10, 14:15'),
			(NULL,'BNI','Visits Main Hall','SOCIAL',57,5,false,'14:15','15:45','2023-03-10',NULL,'TUESDAY',false,'TUESDAY, 2023-03-10, 14:15'),
			(NULL,'BNI','Visits Main Hall','SOCIAL',3,0,true,'14:15','15:45','2023-03-10',NULL,'TUESDAY',false,'TUESDAY, 2023-03-10, 14:15')
	;

	-- update tmp session template table with correct prison id for given code.
	UPDATE tmp_session_template SET prison_id = prison.id FROM prison WHERE tmp_session_template.prison_code = prison.code;

	-- insert data into real session template table from temporary one.
	INSERT INTO session_template(id,reference,visit_room,visit_type,open_capacity,closed_capacity,enhanced,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id,bi_weekly,name)
	SELECT id,CONCAT('-',REGEXP_REPLACE(to_hex((ROW_NUMBER () OVER (ORDER BY id))+2951597050), '(.{3})(?!$)', '\1.','g')) as reference,visit_room,visit_type,open_capacity,closed_capacity,enhanced,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id,bi_weekly,name FROM tmp_session_template order by id;

	-- Sequence updated manually as id's were inserted from temp table
	ALTER SEQUENCE session_template_id_seq RESTART WITH  147;

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
			('BLI','BLI_G2_C2','Wing C - Landing 2 - Cells 1 to 8 and 25 to 32')
	;

	-- update tmp location group table with correct prison id for given code.
	UPDATE tmp_session_location_group SET prison_id = prison.id FROM prison WHERE tmp_session_location_group.prison_code = prison.code;

	-- insert data into real session location group table from temporary one.
	INSERT INTO session_location_group(id,reference,prison_id,name)
				SELECT id,CONCAT('-',REGEXP_REPLACE(to_hex((ROW_NUMBER () OVER (ORDER BY key))+2951597050), '(.{3})(?!$)', '\1~','g')) as reference,prison_id,name FROM tmp_session_location_group order by id;

	-- Sequence updated manually as id's were inserted from temp table
	ALTER SEQUENCE session_location_group_id_seq RESTART WITH  7;


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
			('BLI_G2_C2','C','2','032', NULL)
	;

	-- update tmp location table with correct group_id.
	UPDATE tmp_permitted_session_location SET group_id = tmp_session_location_group.id FROM tmp_session_location_group WHERE tmp_permitted_session_location.group_key = tmp_session_location_group.key;


    -- insert data into real permitted session location table from temporary one.
	INSERT INTO permitted_session_location(id,group_id,level_one_code,level_two_code,level_three_code,level_four_code)
		SELECT id,group_id,level_one_code,level_two_code,level_three_code,level_four_code FROM tmp_permitted_session_location order by id;

	ALTER SEQUENCE permitted_session_location_id_seq RESTART WITH 97;


	-- Create link table data
	INSERT INTO session_to_location_group(session_template_id, group_id)
	SELECT st.id, g.id FROM tmp_session_template st
									   JOIN tmp_session_location_group g ON POSITION(g.key  IN st.locationKeys)<>0 ORDER BY st.id,g.id;

	-- Drop temporary tables
	DROP TABLE tmp_session_template;
	DROP TABLE tmp_session_location_group;
	DROP TABLE tmp_permitted_session_location;

END;
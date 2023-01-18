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
	<#list sessionRecords as s>
		(<#if s.locationKeys??>'${s.locationKeys}'<#else>NULL</#if>,'${s.prison}','${s.room}','${s.type}',${s.open},${s.closed},${s.enhanced?string},'${s.startTime}','${s.endTime}','${s.startDate}',<#if s.endDate??>'${s.endDate}'<#else>NULL</#if>,'${s.dayOfWeek}',${s.biWeekly?string})<#if s_has_next>,</#if>
	</#list>;


	UPDATE tmp_session_template SET prison_id = prison.id FROM prison WHERE tmp_session_template.prison_code = prison.code;

	INSERT INTO session_template(id,visit_room,visit_type,open_capacity,closed_capacity,enhanced,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id,bi_weekly)
	SELECT id,visit_room,visit_type,open_capacity,closed_capacity,enhanced,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id,bi_weekly FROM tmp_session_template order by id;
	ALTER SEQUENCE session_template_id_seq RESTART WITH  ${session_template_id_index};


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
	<#list locations as l>
		(<#if l.key??>'${l.key}'<#else>NULL</#if>,'${l.prison}','${l.levelOne}',<#if l.levelTwo??>'${l.levelTwo}'<#else>NULL</#if>,<#if l.levelThree??>'${l.levelThree}'<#else>NULL</#if>, <#if l.levelFour??>'${l.levelFour}'<#else>NULL</#if>)<#if l_has_next>,</#if>
	</#list>;

	UPDATE tmp_permitted_session_location SET prison_id = prison.id FROM prison WHERE tmp_permitted_session_location.prison_code = prison.code;

	INSERT INTO permitted_session_location(id,prison_id,level_one_code,level_two_code,level_three_code,level_four_code)
	SELECT id,prison_id,level_one_code,level_two_code,level_three_code,level_four_code FROM tmp_permitted_session_location order by id;

	ALTER SEQUENCE permitted_session_location_id_seq RESTART WITH ${permitted_session_location_index};


	-- Create link table data
	INSERT INTO session_to_permitted_location(session_template_id, location_group ,permitted_session_location_id)
	SELECT st.id, l.key, l.id FROM tmp_session_template st
									   JOIN tmp_permitted_session_location l ON POSITION(l.key  IN st.locationKeys)<>0 ORDER BY st.id,l.id;


	-- Drop temporary tables
	DROP TABLE tmp_session_template;
	DROP TABLE tmp_permitted_session_location;

END;
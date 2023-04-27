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
        TRUNCATE TABLE session_template  RESTART IDENTITY CASCADE;
        TRUNCATE TABLE permitted_session_location  RESTART IDENTITY CASCADE;
        TRUNCATE TABLE session_prisoner_category  RESTART IDENTITY CASCADE;

    <#list prisonCodes as pc>
        INSERT INTO prison(code, active) SELECT '${pc}', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = '${pc}');
    </#list>

        -- Creating session template data
        CREATE TEMP TABLE tmp_session_template(
             id                serial        NOT NULL PRIMARY KEY,
             locationKeys      VARCHAR       ,
             categoryKeys      VARCHAR       ,
             prison_code       VARCHAR(6)    NOT NULL,
             prison_id         int    ,
             visit_room    VARCHAR(255),
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

        INSERT INTO tmp_session_template (locationKeys, categoryKeys, prison_code, visit_room, visit_type, open_capacity, closed_capacity,enhanced, start_time, end_time, valid_from_date, valid_to_date, day_of_week,bi_weekly,name)
        VALUES
        <#list sessionRecords as s>
            (<#if s.locationKeys??>'${s.locationKeys}'<#else>NULL</#if>,<#if s.categoryKeys??>'${s.categoryKeys}'<#else>NULL</#if>,'${s.prisonCode}','${s.visitRoom}','${s.type}',${s.open},${s.closed},${s.enhanced?string},'${s.startTime}','${s.endTime}','${s.startDate}',<#if s.endDate??>'${s.endDate}'<#else>NULL</#if>,'${s.dayOfWeek}',${s.biWeekly?string},'${s.dayOfWeek}, ${s.startDate}, ${s.startTime}')<#if s_has_next>,</#if>
        </#list>;

        -- update tmp session template table with correct prison id for given code.
        UPDATE tmp_session_template SET prison_id = prison.id FROM prison WHERE tmp_session_template.prison_code = prison.code;

        -- insert data into real session template table from temporary one.
        INSERT INTO session_template(id,reference,visit_room,visit_type,open_capacity,closed_capacity,enhanced,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id,bi_weekly,name)
        SELECT id,CONCAT('-',REGEXP_REPLACE(to_hex((ROW_NUMBER () OVER (ORDER BY id))+2951597050), '(.{3})(?!$)', '\1.','g')) as reference,visit_room,visit_type,open_capacity,closed_capacity,enhanced,start_time,end_time,valid_from_date,valid_to_date,day_of_week,prison_id,bi_weekly,name FROM tmp_session_template order by id;

        -- Sequence updated manually as id's were inserted from temp table
        ALTER SEQUENCE session_template_id_seq RESTART WITH  ${session_template_id_index};

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
        <#list groups as g>
            ('${g.prisonCode}','${g.key}','${g.name}')<#if g_has_next>,</#if>
        </#list>;

        -- update tmp location group table with correct prison id for given code.
        UPDATE tmp_session_location_group SET prison_id = prison.id FROM prison WHERE tmp_session_location_group.prison_code = prison.code;

        -- insert data into real session location group table from temporary one.
        INSERT INTO session_location_group(id,reference,prison_id,name)
                    SELECT id,CONCAT('-',REGEXP_REPLACE(to_hex((ROW_NUMBER () OVER (ORDER BY key))+2951597050), '(.{3})(?!$)', '\1~','g')) as reference,prison_id,name FROM tmp_session_location_group order by id;

        -- Sequence updated manually as id's were inserted from temp table
        ALTER SEQUENCE session_location_group_id_seq RESTART WITH  ${session_location_group_id_index};


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
        <#list locations as l>
            ('${l.groupKey}','${l.levelOne}',<#if l.levelTwo??>'${l.levelTwo}'<#else>NULL</#if>,<#if l.levelThree??>'${l.levelThree}'<#else>NULL</#if>, <#if l.levelFour??>'${l.levelFour}'<#else>NULL</#if>)<#if l_has_next>,</#if>
        </#list>;

        -- update tmp location table with correct group_id.
        UPDATE tmp_permitted_session_location SET group_id = tmp_session_location_group.id FROM tmp_session_location_group WHERE tmp_permitted_session_location.group_key = tmp_session_location_group.key;


        -- insert data into real permitted session location table from temporary one.
        INSERT INTO permitted_session_location(id,group_id,level_one_code,level_two_code,level_three_code,level_four_code)
            SELECT id,group_id,level_one_code,level_two_code,level_three_code,level_four_code FROM tmp_permitted_session_location order by id;

        ALTER SEQUENCE permitted_session_location_id_seq RESTART WITH ${permitted_session_location_index};


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
    <#if (categoryGroups?size > 0)>
        INSERT INTO tmp_session_category_group (prison_code,key,name)
        VALUES
        <#list categoryGroups as g>
            ('${g.prisonCode}','${g.key}','${g.name}')<#if g_has_next>,</#if>
        </#list>;
    </#if>

    -- update tmp category group table with correct prison id for given code.
    UPDATE tmp_session_category_group SET prison_id = prison.id FROM prison WHERE tmp_session_category_group.prison_code = prison.code;

    -- insert data into real session category group table from temporary one.
    INSERT INTO session_category_group(id,reference,prison_id,name)
    SELECT id,CONCAT('-',REGEXP_REPLACE(to_hex((ROW_NUMBER () OVER (ORDER BY key))+2951597050), '(.{3})(?!$)', '\1~','g')) as reference,prison_id,name FROM tmp_session_category_group order by id;

    -- Sequence updated manually as id's were inserted from temp table
    ALTER SEQUENCE session_category_group_id_seq RESTART WITH  ${session_category_group_id_index};


    -- Create session prisoner category data
    CREATE TABLE tmp_session_prisoner_category (
    id                serial        NOT NULL PRIMARY KEY,
    group_key        VARCHAR(100)  NOT NULL,
    group_id          int,
    code    VARCHAR(100) NOT NULL
    );

    <#if (categories?size > 0)>
        INSERT INTO tmp_session_prisoner_category (group_key,code)
        VALUES
        <#list categories as l>
            ('${l.groupKey}','${l.prisonerCategoryType}')<#if l_has_next>,</#if>
        </#list>;
    </#if>

    -- update tmp prisoner category table with correct group_id.
    UPDATE tmp_session_prisoner_category SET group_id = tmp_session_category_group.id FROM tmp_session_category_group WHERE tmp_session_prisoner_category.group_key = tmp_session_category_group.key;


    -- insert data into real prisoner category table from temporary one.
    INSERT INTO session_prisoner_category(id,session_category_group_id,code)
    SELECT id,group_id,code FROM tmp_session_prisoner_category order by id;

    ALTER SEQUENCE session_prisoner_category_id_seq RESTART WITH ${session_prisoner_category_index};


    -- Create link table data
    INSERT INTO session_to_category_group(session_template_id, session_category_group_id)
    SELECT st.id, g.id FROM tmp_session_template st
    JOIN tmp_session_category_group g ON POSITION(g.key  IN st.categoryKeys)<>0 ORDER BY st.id,g.id;

    -- Category group names are only descriptions they need to be updated when the group categories change

    -- Drop temporary tables
        DROP TABLE tmp_session_template;
        DROP TABLE tmp_session_location_group;
        DROP TABLE tmp_permitted_session_location;
        DROP TABLE tmp_session_category_group;
        DROP TABLE tmp_session_prisoner_category;
    END;
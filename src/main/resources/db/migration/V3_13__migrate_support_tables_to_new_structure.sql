-- Create tmp table for visit support
CREATE TEMP TABLE IF NOT EXISTS tmp_visit_support
(
    id        		serial		NOT NULL PRIMARY KEY,
    visit_id		integer		NOT null,
    description 	text		NOT NULL
);


-- Insert data into tmp visit support table with migration to just description from type
INSERT INTO tmp_visit_support (visit_id, description)
SELECT
    s.visit_id,
    trim(trailing '.' FROM CASE WHEN (st.name = 'OTHER') THEN INITCAP(TRIM(s.text)) ELSE TRIM(st.description) END )  AS description
    FROM visit_support s
             JOIN support_type st ON st.name = s.type
    ORDER BY s.id;


-- Create tmp table for application support
CREATE TABLE tmp_application_support
(
    id        		serial		NOT NULL PRIMARY KEY,
    application_id	integer		NOT null,
    description 	text        NOT NULL
);


-- Insert data into tmp application support table with migration to just description from type
INSERT INTO tmp_application_support (application_id, description)
SELECT
    s.application_id,
    trim(trailing '.' FROM CASE WHEN (st.name = 'OTHER') THEN INITCAP(TRIM(s.text)) ELSE TRIM(st.description) END )  AS description
    FROM application_support s
             JOIN support_type st ON st.name = s.TYPE
    ORDER BY s.id;


-- Drop type from visit_support
ALTER TABLE visit_support 		DROP type;

-- Change name of text to description
ALTER TABLE visit_support 		RENAME COLUMN text TO description;

-- Remove old data from visit_support
TRUNCATE TABLE visit_support;

-- Insert new visit support data and amalgamate all descriptions with same id
INSERT INTO visit_support (visit_id, description)
    SELECT visit_id, string_agg(description,'. ') as description FROM tmp_visit_support group by visit_id;

-- Drop type from application_support
ALTER TABLE application_support DROP type;

-- Change name of text to description
ALTER TABLE application_support	RENAME COLUMN text TO description;

-- Remove old data from application_support
TRUNCATE TABLE application_support;

-- Insert new application_support and amalgamate all descriptions with same id
INSERT INTO application_support (application_id, description)
    SELECT application_id, string_agg(description,'. ') as description FROM tmp_application_support group by application_id;

-- Remove temptables
DROP TABLE IF EXISTS tmp_visit_support;
DROP TABLE IF EXISTS tmp_application_support;

-- add constraint of one to one
ALTER TABLE visit_support ADD CONSTRAINT visit_support_unique_constraint UNIQUE (visit_id);
ALTER TABLE application_support ADD CONSTRAINT application_support_unique_constraint UNIQUE (application_id);
CREATE TABLE session_location_group
(
    id                  serial          NOT NULL PRIMARY KEY,
    reference           text            UNIQUE  NOT NULL,
    prison_id           integer         NOT NULL,
    name                varchar(100)    NOT NULL,
    create_timestamp    timestamp       default current_timestamp,
    modify_timestamp    timestamp       default current_timestamp
);

INSERT INTO session_location_group (reference, name, prison_id)
    SELECT REGEXP_REPLACE(to_hex((ROW_NUMBER () OVER (ORDER BY location_group))+2951597050), '(.{3})(?!$)', '\1~','g') as reference,
           link.location_group as name,
           st.prison_id
    FROM session_to_permitted_location link
             JOIN session_template st ON st.id = session_template_id
    group BY link.location_group,st.prison_id;


CREATE TABLE session_to_location_group (
    session_template_id integer NOT NULL,
    group_id integer NOT NULL,
    CONSTRAINT session_to_group_id_pkey PRIMARY KEY (session_template_id, group_id),
    CONSTRAINT template_must_exist  FOREIGN KEY (session_template_id) REFERENCES session_template(id),
    CONSTRAINT group_must_exist      FOREIGN KEY (group_id) REFERENCES session_location_group(id)
);

INSERT INTO session_to_location_group (session_template_id, group_id)
    SELECT stpl.session_template_id AS session_template_id,
           slg.id AS group_id
    FROM session_location_group slg
             JOIN session_to_permitted_location stpl ON stpl.location_group = slg."name" GROUP BY group_id, session_template_id;

ALTER TABLE permitted_session_location ADD group_id integer;

UPDATE permitted_session_location
    SET group_id = subquery.group_id
        FROM (SELECT slg.id AS group_id, stpl.permitted_session_location_id FROM session_location_group slg
            JOIN session_to_permitted_location stpl ON stpl.location_group = slg."name"
                GROUP BY group_id, permitted_session_location_id) AS subquery
    WHERE permitted_session_location.id = subquery.permitted_session_location_id;

ALTER TABLE permitted_session_location ALTER COLUMN group_id SET NOT NULL;

DROP TABLE session_to_permitted_location;

ALTER TABLE session_template ADD create_timestamp timestamp;
ALTER TABLE session_template ADD modify_timestamp timestamp;
ALTER TABLE session_template ADD name varchar(100);
ALTER TABLE session_template ADD reference text UNIQUE ;


UPDATE session_template
    SET reference = subquery.reference
        FROM (SELECT id,REGEXP_REPLACE(to_hex((ROW_NUMBER () OVER (ORDER BY id))+2951597050), '(.{3})(?!$)', '\1.','g') as reference
                    FROM session_template st) AS subquery
    WHERE session_template.id = subquery.id;
ALTER TABLE session_template ALTER COLUMN reference SET NOT NULL;

UPDATE session_template
    SET name = subquery.name
        FROM (SELECT st.id, concat(p.code,' ',st.id,' ',st.day_of_week) as name
                FROM session_template st JOIN prison p on p.id = st.prison_id ) AS subquery
    WHERE session_template.id = subquery.id;

ALTER TABLE session_template ALTER COLUMN name SET NOT NULL;

ALTER TABLE permitted_session_location DROP prison_id;

-- Create new table
CREATE TABLE prison (
    id   serial      NOT NULL PRIMARY KEY,
    code varchar(3)  UNIQUE NOT NULL,
    active boolean   NOT NULL,
    create_timestamp timestamp default current_timestamp,
    modify_timestamp timestamp default current_timestamp
);

-- Copy data over from session_template table
INSERT INTO prison (code, active)
    SELECT prison_id, true FROM session_template GROUP BY prison_id;

-- Change session_template to use new table instead of string code/id

ALTER TABLE session_template RENAME COLUMN prison_id TO prison_code;
ALTER TABLE session_template ADD prison_id integer;

UPDATE session_template SET prison_id = prison.id
    FROM prison
    WHERE session_template.prison_code = prison.code;

ALTER TABLE session_template ALTER COLUMN prison_id SET NOT NULL;

ALTER TABLE session_template DROP prison_code;

-- Change visit to use new table instead of string code/id

ALTER TABLE visit RENAME COLUMN prison_id TO prison_code;
ALTER TABLE visit ADD prison_id integer;

UPDATE visit SET prison_id = prison.id
    FROM prison
    WHERE visit.prison_code = prison.code;

ALTER TABLE visit ALTER COLUMN prison_id SET NOT NULL;

ALTER TABLE visit DROP prison_code;
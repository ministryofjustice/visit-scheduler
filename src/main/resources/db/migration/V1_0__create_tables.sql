-- placeholder table for testing - will be replaced
CREATE TABLE visit
(
    id              serial      NOT NULL PRIMARY KEY,
    prisoner_id     VARCHAR(80) NOT NULL,
    visit_date_time timestamp with time zone,
    active          BOOLEAN     NOT NULL
);

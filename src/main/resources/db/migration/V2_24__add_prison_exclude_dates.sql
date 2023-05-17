CREATE TABLE prison_exclude_date
(
    id                  serial           NOT NULL PRIMARY KEY,
    prison_id           integer          NOT NULL,
    exclude_date        date             NOT NULL
);



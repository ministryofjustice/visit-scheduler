CREATE TABLE session_template_exclude_date
(
    id                            serial           NOT NULL PRIMARY KEY,
    session_template_id           integer          NOT NULL,
    exclude_date                  date             NOT NULL,
    actioned_by                   VARCHAR(60)      NOT NULL
);

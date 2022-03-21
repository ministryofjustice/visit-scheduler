CREATE TABLE session_exception
(
    id                      serial          NOT NULL PRIMARY KEY,
    session_template_id     serial,
    prison_id               VARCHAR(6)      NOT NULL,
    open_capacity           integer         NOT NULL,
    closed_capacity         integer         NOT NULL,
    end_date                date,
    start_date              date            NOT NULL,
    expiry_date             date,
    reason                  text
);
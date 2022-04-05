CREATE TABLE visit_support
(
    id                  serial          NOT NULL PRIMARY KEY,
    visit_id            integer         NOT NULL,
    type                VARCHAR(80)     NOT NULL,
    text                text,
    UNIQUE (visit_id, type)
);

CREATE INDEX idx_visit_support_visit_id ON visit_support(visit_id);
CREATE TABLE visit_visitor
(
    id                      serial          NOT NULL PRIMARY KEY,
    visit_id                integer         NOT NULL,
    nomis_Person_id         integer         NOT NULL,
    UNIQUE (visit_id, nomis_Person_id)
);

CREATE INDEX idx_visit_visitor_visit_id ON visit_visitor(visit_id);

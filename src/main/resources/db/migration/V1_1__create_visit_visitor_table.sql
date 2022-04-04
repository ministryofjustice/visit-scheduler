CREATE TABLE visit_visitor
(
    id                      serial          NOT NULL PRIMARY KEY,
    visit_id                integer         NOT NULL,
    nomis_Person_id         integer         NOT NULL,
    lead_visitor            BOOLEAN
);

CREATE INDEX idx_visit_visitor_visit_id ON visit_visitor(visit_id);
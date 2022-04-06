CREATE TABLE legacy_data
(
    id                  serial           NOT NULL PRIMARY KEY,
    visit_id            integer          NOT NULL,
    lead_person_id      integer          NOT NULL,
    UNIQUE (visit_id, lead_person_id)
);

CREATE INDEX idx_visit_legacy_visit_id ON legacy_data(visit_id);

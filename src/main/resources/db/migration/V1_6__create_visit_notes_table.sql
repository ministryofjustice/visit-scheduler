CREATE TABLE visit_notes
(
    id                  serial           NOT NULL PRIMARY KEY,
    visit_id            integer          NOT NULL,
    type                VARCHAR(80)      NOT NULL,
    text                text             NOT NULL,
    UNIQUE (visit_id, type)
);

CREATE INDEX idx_visit_notes_visit_id ON visit_notes(visit_id);

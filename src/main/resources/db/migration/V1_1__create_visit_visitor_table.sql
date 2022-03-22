CREATE TABLE visit_visitor
(
    visit_id                text            NOT NULL,
    nomis_Person_id         integer         NOT NULL,
    lead_visitor            BOOLEAN,
    PRIMARY KEY (visit_id, nomis_person_id)
);
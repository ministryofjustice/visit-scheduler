CREATE TABLE visit_contact
(
    visit_id        integer     NOT NULL,
    contact_name    VARCHAR(80) NOT NULL,
    contact_phone   VARCHAR(40) NOT NULL,
    PRIMARY KEY (visit_id)
);

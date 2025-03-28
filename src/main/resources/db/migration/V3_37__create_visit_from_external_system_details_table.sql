CREATE TABLE visit_from_external_system_details
(
    visit_id         BIGINT       NOT NULL,
    client_reference VARCHAR(255) NOT NULL,
    CONSTRAINT pk_visit_from_external_system_details PRIMARY KEY (visit_id)
);

ALTER TABLE visit_from_external_system_details
    ADD CONSTRAINT uc_visit_from_external_system_details_visit UNIQUE (visit_id);

ALTER TABLE visit_from_external_system_details
    ADD CONSTRAINT FK_VISIT_FROM_EXTERNAL_SYSTEM_DETAILS_ON_VISIT FOREIGN KEY (visit_id) REFERENCES visit (id);
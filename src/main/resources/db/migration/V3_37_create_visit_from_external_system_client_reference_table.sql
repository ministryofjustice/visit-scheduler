CREATE TABLE visit_from_external_system_client_reference
(
    visit_id         BIGINT       NOT NULL,
    client_reference VARCHAR(255) NOT NULL,
    CONSTRAINT pk_visit_from_external_system_client_reference PRIMARY KEY (visit_id)
);

ALTER TABLE visit_from_external_system_client_reference
    ADD CONSTRAINT uc_visit_from_external_system_client_reference_visit UNIQUE (visit_id);

ALTER TABLE visit_from_external_system_client_reference
    ADD CONSTRAINT FK_VISIT_FROM_EXTERNAL_SYSTEM_CLIENT_REFERENCE_ON_VISIT FOREIGN KEY (visit_id) REFERENCES visit (id);
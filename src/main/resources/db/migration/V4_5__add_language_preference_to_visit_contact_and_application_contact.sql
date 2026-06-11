ALTER TABLE application_contact
    ADD COLUMN language_preference VARCHAR(2) NOT NULL DEFAULT 'en';

ALTER TABLE visit_contact
    ADD COLUMN language_preference VARCHAR(2) NOT NULL DEFAULT 'en';
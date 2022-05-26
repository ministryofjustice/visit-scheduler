-- *** THIS SHOULD NEVER BE RUN IN LIVE ***
-- Start a transaction
BEGIN;

    SET SCHEMA 'public';

    DELETE FROM legacy_data;
    DELETE FROM visit;
    DELETE FROM visit_contact;
    DELETE FROM visit_notes;
    DELETE FROM visit_support;
    DELETE FROM visit_visitor;

    ALTER SEQUENCE legacy_data_id_seq RESTART WITH 1;
    ALTER SEQUENCE visit_id_seq RESTART WITH 1;
    ALTER SEQUENCE visit_contact_id_seq RESTART WITH 1;
    ALTER SEQUENCE visit_notes_id_seq RESTART WITH 1;
    ALTER SEQUENCE visit_support_id_seq RESTART WITH 1;
    ALTER SEQUENCE visit_visitor_id_seq RESTART WITH 1;

-- Commit the change
END;
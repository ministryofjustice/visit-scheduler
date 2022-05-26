-- *** THIS WILL REMOVE ALL MIGRATED DATA FROM THE VSIP DB ****
-- start a transaction
BEGIN;

    set schema 'public';

    DELETE FROM visit WHERE id IN (SELECT visit_id FROM legacy_data);
    DELETE FROM visit_contact WHERE visit_id IN (SELECT visit_id FROM legacy_data);
    DELETE FROM visit_notes WHERE visit_id IN (SELECT visit_id FROM legacy_data);
    DELETE FROM visit_support WHERE visit_id IN (SELECT visit_id FROM legacy_data);
    DELETE FROM visit_visitor WHERE visit_id IN (SELECT visit_id FROM legacy_data);
    DELETE FROM legacy_data;

-- commit the change
COMMIT;
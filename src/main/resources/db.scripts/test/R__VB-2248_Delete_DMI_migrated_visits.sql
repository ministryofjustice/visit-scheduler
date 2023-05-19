-- *** THIS SHOULD NEVER BE RUN IN LIVE ***
-- Start a transaction
BEGIN;

    SET SCHEMA 'public';

    -- Create
    CREATE TEMP TABLE tmp_delete_migrated_visits(visit_id int not null);

        -- Insert
    INSERT INTO tmp_delete_migrated_visits(visit_id)
        SELECT v.id FROM visit v
                        JOIN legacy_data ld ON ld.visit_id = v.id
                        JOIN prison p ON p.id = v.prison_id
                        WHERE p.code = 'DMI'
    -- Delete
    DELETE FROM visit WHERE id IN (SELECT visit_id FROM tmp_delete_migrated_visits);
    DELETE FROM visit_contact WHERE visit_id IN (SELECT visit_id FROM tmp_delete_migrated_visits);
    DELETE FROM visit_notes WHERE visit_id IN (SELECT visit_id FROM tmp_delete_migrated_visits);
    DELETE FROM visit_support WHERE visit_id IN (SELECT visit_id FROM tmp_delete_migrated_visits);
    DELETE FROM visit_visitor WHERE visit_id IN (SELECT visit_id FROM tmp_delete_migrated_visits);
    DELETE FROM legacy_data WHERE visit_id IN (SELECT visit_id FROM tmp_delete_migrated_visits);

    -- Drop temporary tables
    DROP TABLE  tmp_visits_with_sessions;
    DROP TABLE  tmp_visits_to_sessions;
    DROP TABLE  tmp_visits_to_dup_sessions;

-- Commit the change
END;
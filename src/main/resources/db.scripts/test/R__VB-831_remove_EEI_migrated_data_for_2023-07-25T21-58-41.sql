-- Start a transaction
BEGIN;

    SET SCHEMA 'public';

    -- Create
    CREATE TEMP TABLE tmp_delete_migrated_visits(visit_id int not null);

            -- Insert
    INSERT INTO tmp_delete_migrated_visits(visit_id)
        SELECT ld.visit_id FROM legacy_data ld
                                 JOIN visit v ON v.id = ld.visit_id
                                 JOIN prison p ON p.id  = v.prison_id
        WHERE p.code = 'EEI' AND ld.migrate_date_time>='2023-07-25T21:58:41'

    -- Delete
    DELETE FROM visit WHERE id IN (SELECT visit_id FROM tmp_delete_migrated_visits);
    DELETE FROM visit_contact WHERE visit_id IN (SELECT visit_id FROM tmp_delete_migrated_visits);
    DELETE FROM visit_notes WHERE visit_id IN (SELECT visit_id FROM tmp_delete_migrated_visits);
    DELETE FROM visit_support WHERE visit_id IN (SELECT visit_id FROM tmp_delete_migrated_visits);
    DELETE FROM visit_visitor WHERE visit_id IN (SELECT visit_id FROM tmp_delete_migrated_visits);
    DELETE FROM legacy_data WHERE visit_id IN (SELECT visit_id FROM tmp_delete_migrated_visits);

    -- Drop temporary tables
    DROP TABLE  tmp_delete_migrated_visits;

-- Commit the change
END;
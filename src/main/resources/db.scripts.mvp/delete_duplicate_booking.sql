BEGIN;

    SET SCHEMA 'public';

    CREATE TEMP TABLE tmp_cfi_visit_ids_to_be_deleted(
        visit_id         int
    );

    --selection criteria for visits to be deleted
    INSERT INTO tmp_cfi_visit_ids_to_be_deleted (visit_id) (
        select v.id from visit v
                join legacy_data ld on ld.visit_id = v.id
                where v.reference = 'jr-wd-ri-zl'
    );

    DELETE FROM visit_contact WHERE visit_id in (select visit_id FROM tmp_cfi_visit_ids_to_be_deleted);
    DELETE FROM visit_notes WHERE visit_id in (select visit_id FROM tmp_cfi_visit_ids_to_be_deleted);
    DELETE FROM visit_support WHERE visit_id in (select visit_id FROM tmp_cfi_visit_ids_to_be_deleted);
    DELETE FROM visit_visitor WHERE visit_id in (select visit_id FROM tmp_cfi_visit_ids_to_be_deleted);
    DELETE FROM visit WHERE id in (select visit_id FROM tmp_cfi_visit_ids_to_be_deleted);
    DELETE FROM legacy_data d where visit_id in (select visit_id FROM tmp_cfi_visit_ids_to_be_deleted);

    -- Drop temporary tables
    DROP TABLE tmp_cfi_visit_ids_to_be_deleted;

END;

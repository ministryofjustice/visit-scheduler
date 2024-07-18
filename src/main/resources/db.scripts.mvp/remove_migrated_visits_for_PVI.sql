BEGIN;

    SET SCHEMA 'public';


    CREATE TEMP TABLE tmp_visit_ids_to_be_deleted(
        visit_id         int,
        booking_reference text
    );


    CREATE TEMP TABLE tmp_application_ids_to_be_deleted(
            application_id         int,
            application_reference text
    );

    --selection criteria for applications to be deleted
    INSERT INTO tmp_application_ids_to_be_deleted (application_id, application_reference) (
        SELECT a.id, a.reference  from application a
                                           join visit v on a.visit_id = v.id
                                           join legacy_data ld on ld.visit_id = v.id
                                           join prison p on p.id=v.prison_id and p.code = 'PVI'
        where ld.id is not null
    );

    DELETE FROM application_contact WHERE application_id in (select application_id FROM tmp_application_ids_to_be_deleted);
    DELETE FROM application_support WHERE application_id in (select application_id FROM tmp_application_ids_to_be_deleted);
    DELETE FROM application_visitor WHERE application_id in (select application_id FROM tmp_application_ids_to_be_deleted);
    DELETE FROM application WHERE id in (select application_id FROM tmp_application_ids_to_be_deleted);
    DELETE FROM event_audit ev where application_reference in (select application_reference FROM tmp_application_ids_to_be_deleted);


    --selection criteria for visits to be deleted
    INSERT INTO tmp_visit_ids_to_be_deleted (visit_id,booking_reference) (
        SELECT v.id, v.reference  from visit v
                              join legacy_data ld on ld.visit_id = v.id
                              join prison p on p.id=v.prison_id and p.code = 'PVI'
        where ld.id is not null
    );


    DELETE FROM visit_contact WHERE visit_id in (select visit_id FROM tmp_visit_ids_to_be_deleted);
    DELETE FROM visit_notes WHERE visit_id in (select visit_id FROM tmp_visit_ids_to_be_deleted);
    DELETE FROM visit_support WHERE visit_id in (select visit_id FROM tmp_visit_ids_to_be_deleted);
    DELETE FROM visit_visitor WHERE visit_id in (select visit_id FROM tmp_visit_ids_to_be_deleted);
    DELETE FROM visit WHERE id in (select visit_id FROM tmp_visit_ids_to_be_deleted);
    DELETE FROM legacy_data d where visit_id in (select visit_id FROM tmp_visit_ids_to_be_deleted);
    DELETE FROM event_audit ev where booking_reference in (select booking_reference FROM tmp_visit_ids_to_be_deleted);

    -- Drop temporary tables
    DROP TABLE tmp_visit_ids_to_be_deleted;

END;

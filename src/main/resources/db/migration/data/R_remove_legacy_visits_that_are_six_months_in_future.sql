-- *** THIS WILL REMOVE ALL MIGRATED DATA FROM THE VSIP DB ****
-- start a transaction
BEGIN;

    set schema 'public';

    CREATE TEMP TABLE tmp_future_visits(visit_id INT NOT NULL);

    INSERT INTO tmp_future_visits(visit_id)
                SELECT v.id  FROM visit v
                        JOIN legacy_data ld ON ld.visit_id = v.id
                        WHERE v.visit_start > current_date + INTERVAL '6 months';

    DELETE FROM visit WHERE id IN (SELECT visit_id FROM tmp_future_visits);
    DELETE FROM visit_contact WHERE visit_id IN (SELECT visit_id FROM tmp_future_visits);
    DELETE FROM visit_notes WHERE visit_id IN (SELECT visit_id FROM tmp_future_visits);
    DELETE FROM visit_support WHERE visit_id IN (SELECT visit_id FROM tmp_future_visits);
    DELETE FROM visit_visitor WHERE visit_id IN (SELECT visit_id FROM tmp_future_visits);
    DELETE FROM legacy_data WHERE visit_id IN (SELECT visit_id FROM tmp_future_visits);

    DROP TABLE tmp_future_visits;

-- commit the change
COMMIT;
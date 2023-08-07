BEGIN;

SET SCHEMA 'public';

    CREATE TEMP TABLE tmp_future_visits_to_be_deleted(
    visit_id         int
);

--delete all future visits that are greater than 6 months away.
INSERT INTO tmp_future_visits_to_be_deleted (visit_id) (
    SELECT v.id FROM visit v
      LEFT JOIN legacy_data ld ON ld.visit_id = v.id
    WHERE  v.visit_start >= now()  + interval '6 months'
      and v.visit_Status = 'BOOKED'
);

DELETE FROM event_audit WHERE visit_id in (select visit_id FROM tmp_future_visits_to_be_deleted);
DELETE FROM visit_contact WHERE visit_id in (select visit_id FROM tmp_future_visits_to_be_deleted);
DELETE FROM visit_notes WHERE visit_id in (select visit_id FROM tmp_future_visits_to_be_deleted);
DELETE FROM visit_support WHERE visit_id in (select visit_id FROM tmp_future_visits_to_be_deleted);
DELETE FROM visit_visitor WHERE visit_id in (select visit_id FROM tmp_future_visits_to_be_deleted);
DELETE FROM visit WHERE id in (select visit_id FROM tmp_future_visits_to_be_deleted);
DELETE FROM legacy_data d where visit_id in (select visit_id FROM tmp_future_visits_to_be_deleted);

-- Drop temporary tables
DROP TABLE tmp_future_visits_to_be_deleted;
END;

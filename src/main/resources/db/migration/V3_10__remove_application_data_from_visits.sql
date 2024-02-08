
-- Create table
CREATE TEMP TABLE tmp_delete_applications_from_visits(visit_id int not null);

INSERT INTO tmp_delete_applications_from_visits(visit_id)
    SELECT v.id FROM visit v  WHERE v.visit_status  in  ('CHANGING','RESERVED') or (v.visit_status  = 'CANCELLED' and v.outcome_status = 'SUPERSEDED_CANCELLATION');

DELETE FROM visit_contact WHERE visit_id IN (SELECT visit_id FROM tmp_delete_applications_from_visits);
DELETE FROM visit_notes WHERE visit_id IN (SELECT visit_id FROM tmp_delete_applications_from_visits);
DELETE FROM visit_support WHERE visit_id IN (SELECT visit_id FROM tmp_delete_applications_from_visits);
DELETE FROM visit_visitor WHERE visit_id IN (SELECT visit_id FROM tmp_delete_applications_from_visits);
DELETE FROM visit WHERE id IN (SELECT visit_id FROM tmp_delete_applications_from_visits);


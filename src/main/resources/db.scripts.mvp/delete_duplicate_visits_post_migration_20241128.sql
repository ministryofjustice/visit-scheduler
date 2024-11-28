BEGIN;

SET SCHEMA 'public';


CREATE TEMP TABLE tmp_visit_ids_to_be_deleted(
                                                 visit_id         int,
                                                 booking_reference text
);

--selection criteria for visits to be deleted
INSERT INTO tmp_visit_ids_to_be_deleted (visit_id,booking_reference) (
    SELECT v.id, v.reference  from visit v
    join legacy_data ld on ld.visit_id = v.id
    where v.reference in (
      'be-yv-wl-cz',
      'po-yv-wl-fn',
      'so-yv-wl-hm',
      'zx-yv-wl-cg',
      'xa-yv-wl-cn',
      'an-lv-wl-fw',
      'er-av-wl-fd',
      'qm-pj-wl-ue',
      'sa-pj-wl-is',
      'ko-qj-wl-cn',
      'gx-aj-wl-hd',
      'xx-sp-wl-hr',
      'we-wn-wl-hy'
     )
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

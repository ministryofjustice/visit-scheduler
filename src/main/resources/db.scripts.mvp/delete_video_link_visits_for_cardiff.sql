BEGIN;

SET SCHEMA 'public';

CREATE TEMP TABLE tmp_cfi_visit_ids_to_be_deleted(
    visit_id         int
);

--selection criteria is CFI visits with a 30 minute window and room name as 'Video Link'
--these visits should not be on VSIP as these are video calls and not VSIP visits
INSERT INTO tmp_cfi_visit_ids_to_be_deleted (visit_id) (
    select v.id from visit v, prison p
    where v.prison_id = p.id
    and p.code = 'CFI'
    and v.visit_start + interval '30 minutes' = v.visit_end
    and visit_room = 'Video Link'
    and v.id in (select ld.visit_id from legacy_data ld)
);

DELETE FROM visit_contact WHERE visit_id IN (SELECT visit_id FROM legacy_data)  and visit_id in (select visit_id FROM tmp_cfi_visit_ids_to_be_deleted);
DELETE FROM visit_notes WHERE visit_id IN (SELECT visit_id FROM legacy_data)  and visit_id in (select visit_id FROM tmp_cfi_visit_ids_to_be_deleted);
DELETE FROM visit_support WHERE visit_id IN (SELECT visit_id FROM legacy_data) and visit_id in (select visit_id FROM tmp_cfi_visit_ids_to_be_deleted);
DELETE FROM visit_visitor WHERE visit_id IN (SELECT visit_id FROM legacy_data) and visit_id in (select visit_id FROM tmp_cfi_visit_ids_to_be_deleted);
DELETE FROM visit WHERE id IN (SELECT visit_id FROM legacy_data) and id in (select visit_id FROM tmp_cfi_visit_ids_to_be_deleted);
DELETE FROM legacy_data d where visit_id in (select visit_id FROM tmp_cfi_visit_ids_to_be_deleted);

-- Drop temporary tables
DROP TABLE tmp_cfi_visit_ids_to_be_deleted;

END;
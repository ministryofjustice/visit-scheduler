BEGIN;

SET SCHEMA 'public';

CREATE TEMP TABLE tmp_wwi_future_visit_ids_to_be_deleted(
    visit_id         int
);

--delete 5 identified future visits.
INSERT INTO tmp_wwi_future_visit_ids_to_be_deleted (visit_id) (
    select v.id from visit v, prison p
    where v.prison_id = p.id
    and (p.code = 'PNI' or p.code = 'CFI')
    and reference in (
        'ab-nn-yi-ex',
        'gs-ez-rc-gl',
        'ln-lo-qc-bo',
        'ks-po-qc-nx',
        'za-xb-qc-xv'
        )
    and visit_start >= '2029-01-01'
    and v.id in (select ld.visit_id from legacy_data ld)
);

DELETE FROM visit_contact WHERE visit_id in (select visit_id FROM tmp_wwi_future_visit_ids_to_be_deleted);
DELETE FROM visit_notes WHERE visit_id in (select visit_id FROM tmp_wwi_future_visit_ids_to_be_deleted);
DELETE FROM visit_support WHERE visit_id in (select visit_id FROM tmp_wwi_future_visit_ids_to_be_deleted);
DELETE FROM visit_visitor WHERE visit_id in (select visit_id FROM tmp_wwi_future_visit_ids_to_be_deleted);
DELETE FROM visit WHERE id in (select visit_id FROM tmp_wwi_future_visit_ids_to_be_deleted);
DELETE FROM legacy_data d where visit_id in (select visit_id FROM tmp_wwi_future_visit_ids_to_be_deleted);

-- Drop temporary tables
DROP TABLE tmp_wwi_future_visit_ids_to_be_deleted;

END;

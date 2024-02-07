BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'se-zp-va-hm',
        'qr-ns-va-hd',
        'np-nn-va-uj',
        'xk-jn-va-cn',
        'mm-bs-va-un',
        'en-ds-va-fd',
        'ob-ks-va-is'
      );

    UPDATE visit SET session_template_reference = 'gal.uvd.asy'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;

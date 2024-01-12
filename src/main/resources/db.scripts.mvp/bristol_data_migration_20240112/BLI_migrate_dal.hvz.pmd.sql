BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'lb-gn-va-fe',
        'yb-ks-va-ix',
        'dr-gn-va-cn',
        'qm-qn-va-uo',
        'zn-zp-va-fg',
        'ld-nn-va-uz'
      );

    UPDATE visit SET session_template_reference = 'dal.hvz.pmd'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;

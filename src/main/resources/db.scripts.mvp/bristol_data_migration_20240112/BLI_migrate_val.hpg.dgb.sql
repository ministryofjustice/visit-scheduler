BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'nq-qn-va-cq',
        'kv-es-va-hr',
        'sb-ks-va-cp',
        'kw-dn-va-hj',
        'jy-ap-va-cx'
      );

    UPDATE visit SET session_template_reference = 'val.hpg.dgb'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;

BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'pq-gn-va-hs',
        'db-lp-va-fl',
        'kx-bs-va-ha',
        'gx-bs-va-iy',
        'nq-nn-va-fw',
        'ax-lp-va-cw',
        'sd-bn-va-cb',
        'vr-ks-va-fp',
        'zn-es-va-cj',
        'gq-xp-va-cz',
        'op-xp-va-fm',
        'pz-kd-va-fl',
        'ey-lp-va-up',
        'ge-wn-va-cs'
      );

    UPDATE visit SET session_template_reference = 'ral.cqa.oge'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;

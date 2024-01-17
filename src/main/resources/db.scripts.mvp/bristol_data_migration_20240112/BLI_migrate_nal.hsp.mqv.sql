BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'xn-lp-va-fs',
        'db-xs-va-is',
        'ym-bs-va-cp',
        'qn-lp-va-hx'
      );

    UPDATE visit SET session_template_reference = 'nal.hsp.mqv'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;

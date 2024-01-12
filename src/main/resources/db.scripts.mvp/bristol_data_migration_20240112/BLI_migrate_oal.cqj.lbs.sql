BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'pb-xs-va-hz',
        'ow-jn-va-cx',
        'js-lp-va-hw',
        'xv-es-va-fx',
        'dk-jn-va-cr'
      );

    UPDATE visit SET session_template_reference = 'oal.cqj.lbs'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;

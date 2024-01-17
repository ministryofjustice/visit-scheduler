BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'le-xp-va-ul'
      );

    UPDATE visit SET session_template_reference = 'lll.isg.pgm'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;

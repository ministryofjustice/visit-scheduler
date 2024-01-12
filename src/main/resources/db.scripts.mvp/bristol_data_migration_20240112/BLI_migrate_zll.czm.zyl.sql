BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'yb-xs-va-um',
        'ka-lp-va-ud',
        'wb-kn-va-fl',
        'lj-zp-va-cx',
        'mk-ms-va-uy',
        'qy-gn-va-hj',
        'yx-ap-va-iv',
        'sn-bn-va-un',
        'bo-qn-va-cx'
      );

    UPDATE visit SET session_template_reference = 'zll.czm.zyl'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;

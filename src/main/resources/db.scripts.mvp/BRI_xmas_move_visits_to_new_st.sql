BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
                        'do-vo-ea-ca',
                        'rv-jo-ea-hz',
                        'ar-on-pa-uy',
                        'bv-so-ea-fd',
                        've-go-ea-hb',
                        'jr-zd-ea-cm',
                        'xz-jo-ea-ir',
                        'eq-so-ea-hz',
                        'yb-vo-ea-ua'
        );


    UPDATE visit SET session_template_reference = 'xva.imp.ewp'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;
BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'ds-ls-va-ha',
        'nq-xn-va-fd',
        'vw-zp-va-us',
        'sp-xp-va-ha',
        'sk-vn-va-fb',
        'os-lp-va-fn',
        'nw-jn-va-fx',
        'pp-xp-va-hv',
        'os-dn-va-ce',
        'rm-as-va-cm',
        'pm-qn-va-ig',
        'px-ks-va-ca'
    );

    UPDATE visit SET session_template_reference = 'bal.fps.lrp'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;

END;

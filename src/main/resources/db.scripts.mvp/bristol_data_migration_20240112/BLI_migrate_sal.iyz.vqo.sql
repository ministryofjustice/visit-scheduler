BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'vp-xp-va-fb',
        'ds-ln-va-ip',
        'bp-qn-va-hl',
        'gr-dn-va-ug',
        'dv-es-va-fe',
        'sk-ms-va-fg',
        'od-ls-va-fv',
        'an-lp-va-fs',
        'ww-jn-va-cb',
        'xe-xp-va-co',
        'me-zp-va-iz',
        'wq-ap-va-co',
        'zd-lp-va-cn',
        'rd-lp-va-ce',
        'oy-ls-va-ue',
        'ox-xp-va-fa'
      );

    UPDATE visit SET session_template_reference = 'sal.iyz.vqo'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;

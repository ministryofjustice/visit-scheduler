BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'kn-ds-va-cg',
        'sy-ls-va-fx',
        'gp-mn-va-uj',
        'rq-gd-va-ig',
        'je-an-va-fo',
        'xe-mn-va-ip',
        'gl-ks-va-us',
        'dr-ap-va-fg',
        'aw-an-va-im',
        'bm-gn-va-ud',
        'yp-jn-va-cb',
        'sd-ed-va-hd',
        'ga-yd-va-cj',
        'qy-vs-va-cz',
        'xg-on-va-iq',
        'bm-xs-va-ho',
        'gv-es-va-iy',
        'ew-vs-va-id',
        'ws-ln-va-fq',
        'ov-jn-va-ij',
        'ws-bn-va-cx',
        'bm-ns-va-fl',
        'vv-es-va-fy'
      );

    UPDATE visit SET session_template_reference = 'jal.hng.xpr'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;

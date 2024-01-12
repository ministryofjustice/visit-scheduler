BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'rd-en-va-fa',
        'ny-sn-va-cv',
        'pq-bn-va-hg',
        'mk-js-va-uv',
        'ng-on-va-hb',
        'ql-gn-va-il',
        'ql-vn-va-cz',
        'oz-gn-va-co',
        'rw-en-va-fd',
        'or-nn-va-ib',
        'oy-rd-va-id',
        'ew-an-va-fz',
        'ar-nn-va-ur',
        'rj-en-va-fg',
        'dx-bs-va-hd',
        'jj-en-va-ir',
        'ok-jd-va-up',
        'zg-mn-va-ib',
        'vj-es-va-is',
        'jx-as-va-he',
        'be-mn-va-fr',
        'zr-ks-va-uj',
        'vm-ks-va-hy',
        'ww-ms-va-im',
        'ar-vd-va-fa',
        'zq-ln-va-cb',
        'md-zp-va-fs',
        'zl-wn-va-cj',
        'gb-nn-va-hs',
        'yn-zp-va-cw',
        'rb-xs-va-ub',
        'gm-gn-va-fr',
        'vo-dn-va-iw',
        'dd-zp-va-ha',
        'nr-bn-va-uv',
        'ob-nn-va-hr',
        'gv-jn-va-cz',
        'wo-zp-va-us',
        'bm-qn-va-ir'
      );

    UPDATE visit SET session_template_reference = 'mal.ueo.jlj'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;

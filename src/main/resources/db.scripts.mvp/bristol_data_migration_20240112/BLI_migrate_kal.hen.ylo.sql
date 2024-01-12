BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'ak-ms-va-hg',
        'yw-ap-va-hn',
        'sr-ks-va-ua',
        'py-sn-va-up',
        'lo-xp-va-io',
        'mr-zp-va-ud',
        'kq-zp-va-fo',
        'lb-ed-va-ud',
        'vg-jn-va-ie',
        'dg-jn-va-id',
        'aq-vd-va-cg',
        'ym-gn-va-ug',
        'lq-gn-va-ud',
        'ox-gn-va-cp',
        'zx-gn-va-fq',
        'jg-mn-va-fd',
        'pw-en-va-fp',
        'bj-ss-va-hw',
        'kr-nn-va-hj',
        'bb-ns-va-in',
        'xb-bn-va-cs',
        'gv-mn-va-ur',
        'ov-mn-va-cy',
        'rj-mn-va-cx',
        'bv-vs-va-ub',
        'px-bs-va-uz',
        'we-zp-va-cv',
        'ax-ap-va-uv',
        'ok-zp-va-fe',
        'ge-zp-va-ie',
        'mg-zp-va-ix',
        'pn-lp-va-io'
      );

    UPDATE visit SET session_template_reference = 'kal.hen.ylo'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;

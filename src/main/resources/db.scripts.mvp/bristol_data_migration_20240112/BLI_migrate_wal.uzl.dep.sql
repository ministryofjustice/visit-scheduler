BEGIN;

    CREATE TEMP TABLE tmp_visits(visit_id int not null);

    INSERT INTO tmp_visits(visit_id)
    SELECT v.id from visit v, prison p
    WHERE v.prison_id = p.id
      AND p.code = 'BLI'
      AND reference in (
        'sr-pd-va-ha',
        'pd-ls-va-uj',
        'vl-gn-va-uj',
        'zd-en-va-ho',
        'nv-ap-va-cr',
        'qz-wd-va-cj',
        'kb-zp-va-cp',
        'yg-mn-va-cr',
        'we-kn-va-hz',
        'qk-ap-va-hm',
        'lz-lp-va-ua',
        'ey-kn-va-un',
        'zv-vn-va-fx',
        'aj-jn-va-hd',
        'wy-ap-va-us',
        'kg-on-va-fs',
        'na-ln-va-cp'
      );

    UPDATE visit SET session_template_reference = 'wal.uzl.dep'
        FROM tmp_visits tmp WHERE visit.id = tmp.visit_id;

    -- drop temporary tables
    DROP TABLE  tmp_visits;


END;

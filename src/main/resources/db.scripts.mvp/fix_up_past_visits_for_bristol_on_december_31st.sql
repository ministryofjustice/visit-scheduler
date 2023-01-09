-- VB-1598 View establishment schedule change from 9:30/10:30 to 15:30/16:30
BEGIN;

    UPDATE visit
        SET visit_start = date_trunc('day', s.visit_start) + '15:30:00',
            visit_end = date_trunc('day', s.visit_end) + '16:30:00'
            FROM (SELECT id,visit_start,visit_end FROM visit WHERE
                    WHERE reference in ('jk-vs-lh-xa','nk-ws-lh-ow','oa-ks-li-dg','rv-ws-li-nv','qk-ws-lh-om','vk-ws-lu-xv','ok-ws-lf-ez','qy-ws-lc-sq','bv-ws-lh-dp','wv-ws-lu-ld','za-ks-lc-mx','mk-ws-li-zp','yk-ws-lu-ra')
             ) AS s
        WHERE visit.id = s.id AND prison_id = 2;

END;

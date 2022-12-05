-- for old db structure
BEGIN;

    UPDATE visit
    SET visit_start = date_trunc('day', s.visit_start) + '14:00:00',
        visit_end = date_trunc('day', s.visit_end) + '16:00:00'
        FROM (SELECT id,visit_start,visit_end FROM visit WHERE visit_start > '2022-12-19' AND visit_start < '2023-1-03' AND to_char(visit_start,'HH24:MI') = '13:45' and
                    trim(to_char(visit_start, 'Day')) IN ('Monday','Wednesday','Saturday','Sunday') ) AS s
    WHERE visit.id = s.id AND prison_id = 'HEI';


    UPDATE visit
    SET visit_start = date_trunc('day', s.visit_start) + '09:00:00',
        visit_end = date_trunc('day', s.visit_end) + '11:00:00'
        FROM (SELECT id,visit_start,visit_end FROM visit WHERE visit_start > '2022-12-19' AND visit_start < '2023-1-03' AND to_char(visit_start,'HH24:MI') = '09:00' and
                    trim(to_char(visit_start, 'Day')) IN ('Friday') ) AS s
    WHERE visit.id = s.id AND prison_id = 'HEI';


END;
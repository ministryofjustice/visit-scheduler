BEGIN;
    -- VB-2064
    -- Remove day from HMP Morton Hall schedule and cancel bookings

    SET SCHEMA 'public';

    UPDATE visit SET visit_status = 'CANCELLED', outcome_status = 'ADMINISTRATIVE_ERROR'
    WHERE id IN (SELECT v.id  FROM visit v
                                       JOIN prison p ON p.id = v.prison_id
                 WHERE 	v.visit_start  >= '2023-04-06' AND
                         v.visit_end < '2023-04-07' AND
                         p.code = 'MHI');
END;
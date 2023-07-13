-- This sql makes sure visits in future aline up to the latest session times and session templates.

UPDATE visit
    SET visit_start = date_trunc('day', tmp.visit_start) + '14:00:00',
    SET visit_end = date_trunc('day', tmp.visit_end) + '16:00:00',
    SET session_template_reference = <PLEASE NETER ST REF FOR TUESDAY>
FROM (SELECT v.* FROM visit v
    LEFT JOIN legacy_data ld ON ld.visit_id = v.id
    JOIN prison p ON p.id = v.prison_id
    WHERE p.code = 'MHI' AND v.visit_start > CURRENT_DATE AND ld.id IS NULL) AS tmp
WHERE tmp.id = visit.id and UPPER(TRIM(To_Char(tmp.visit_start , 'DAY'))) = 'TUESDAY';

UPDATE visit
    SET visit_start = date_trunc('day', tmp.visit_start) + '14:00:00',
    SET visit_end = date_trunc('day', tmp.visit_end) + '16:00:00',
    SET session_template_reference = <PLEASE NETER ST REF FOR THURSDAY>
FROM (SELECT v.* FROM visit v
    LEFT JOIN legacy_data ld ON ld.visit_id = v.id
    JOIN prison p ON p.id = v.prison_id
    WHERE p.code = 'MHI' AND v.visit_start > CURRENT_DATE AND ld.id IS NULL) AS tmp
WHERE tmp.id = visit.id and UPPER(TRIM(To_Char(tmp.visit_start , 'DAY'))) = 'THURSDAY';

UPDATE visit
    SET visit_start = date_trunc('day', tmp.visit_start) + '14:00:00',
    SET visit_end = date_trunc('day', tmp.visit_end) + '16:00:00',
    SET session_template_reference = <PLEASE NETER ST REF FOR SATURDAY>
FROM (SELECT v.* FROM visit v
    LEFT JOIN legacy_data ld ON ld.visit_id = v.id
    JOIN prison p ON p.id = v.prison_id
    WHERE p.code = 'MHI' AND v.visit_start > CURRENT_DATE AND ld.id IS NULL) AS tmp
WHERE tmp.id = visit.id and UPPER(TRIM(To_Char(tmp.visit_start , 'DAY'))) = 'SATURDAY';

UPDATE visit
    SET visit_start = date_trunc('day', tmp.visit_start) + '14:00:00',
    SET visit_end = date_trunc('day', tmp.visit_end) + '16:00:00',
    SET session_template_reference = <PLEASE NETER ST REF FOR SUNDAY>
FROM (SELECT v.* FROM visit v
    LEFT JOIN legacy_data ld ON ld.visit_id = v.id
    JOIN prison p ON p.id = v.prison_id
    WHERE p.code = 'MHI' AND v.visit_start > CURRENT_DATE AND ld.id IS NULL) AS tmp
WHERE tmp.id = visit.id and UPPER(TRIM(To_Char(tmp.visit_start , 'DAY'))) = 'SUNDAY';



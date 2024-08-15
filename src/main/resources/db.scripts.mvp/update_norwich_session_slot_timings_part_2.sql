-- Fix to update Norwich (NWI) from old session template to new session template
-- | old session | new session | day        |
-- | ----------- | ----------- | ---------- |
-- | pvl.fje.sxq | lwl.iqq.asa | Tuesday AM |
-- | jvl.ipq.jmp | ywl.hol.sng | Thursday AM |
-- | kvl.uax.rdz | xwl.crb.esg | Saturday PM |
-- | awl.fqy.bbs | mwl.ueq.ndj | Sunday PM |


BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'lwl.iqq.asa' where session_template_reference = 'pvl.fje.sxq' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'NWI');

UPDATE session_slot set session_template_reference = 'ywl.hol.sng' where session_template_reference = 'jvl.ipq.jmp' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'NWI');

UPDATE session_slot set session_template_reference = 'xwl.crb.esg' where session_template_reference = 'kvl.uax.rdz' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'NWI');

UPDATE session_slot set session_template_reference = 'mwl.ueq.ndj' where session_template_reference = 'awl.fqy.bbs' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'NWI');

END;

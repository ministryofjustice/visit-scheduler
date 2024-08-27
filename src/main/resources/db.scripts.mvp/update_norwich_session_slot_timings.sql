-- Fix to update Norwich (NWI) from old session template to new session template
-- | old session | new session | day        |
-- | ----------- | ----------- | ---------- |
-- | ngl.cmo.vxy | pvl.fje.sxq | Tuesday AM |
-- | pgl.cvs.vjp | evl.fns.jdy | Tuesday PM |
-- | ggl.hqs.sqj | gvl.idr.bml | Wednesday PM |
-- | jgl.upr.vlw | jvl.ipq.jmp | Thursday AM |
-- | vgl.hev.pyw | vvl.uys.rlp | Thursday PM |
-- | wgl.umr.oan | wvl.ian.zyj | Saturday AM |
-- | kgl.ild.ozq | kvl.uax.rdz | Saturday PM |
-- | ajl.uae.qme | zwl.hrz.adn | Sunday AM |
-- | ljl.hzd.svv | awl.fqy.bbs | Sunday PM |


BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'pvl.fje.sxq' where session_template_reference = 'ngl.cmo.vxy' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'NWI');

UPDATE session_slot set session_template_reference = 'evl.fns.jdy' where session_template_reference = 'pgl.cvs.vjp' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'NWI');

UPDATE session_slot set session_template_reference = 'gvl.idr.bml' where session_template_reference = 'ggl.hqs.sqj' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'NWI');

UPDATE session_slot set session_template_reference = 'jvl.ipq.jmp' where session_template_reference = 'jgl.upr.vlw' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'NWI');

UPDATE session_slot set session_template_reference = 'vvl.uys.rlp' where session_template_reference = 'vgl.hev.pyw' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'NWI');

UPDATE session_slot set session_template_reference = 'wvl.ian.zyj' where session_template_reference = 'wgl.umr.oan' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'NWI');

UPDATE session_slot set session_template_reference = 'kvl.uax.rdz' where session_template_reference = 'kgl.ild.ozq' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'NWI');

UPDATE session_slot set session_template_reference = 'zwl.hrz.adn' where session_template_reference = 'ajl.uae.qme' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'NWI');

UPDATE session_slot set session_template_reference = 'awl.fqy.bbs' where session_template_reference = 'ljl.hzd.svv' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'NWI');

END;

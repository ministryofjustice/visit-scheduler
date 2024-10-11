-- Fix to update Lindholme (LHI) from old session template to new session template
--| **new session** | **old session** | **day**   |
--| --------------- | --------------- | --------- |
--| syy.czq.ols     | nea.hpz.wdq     | Monday    |
--| nyy.isd.zpz     | pea.uya.sae     | Wednesday |
--| pyy.cjx.lxz     | eea.hmq.rsa     | Thursday  |
--| eyy.ubx.esm     | gea.fas.qgo     | Friday    |
--| gyy.uep.wzb     | jea.hqp.svd     | Saturday  |
--| jyy.csm.mvx     | vea.umo.odx     | Sunday    |

BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'syy.czq.ols' where session_template_reference = 'nea.hpz.wdq' and slot_date >= '2024-10-11' and prison_id = (select id from prison where code = 'LHI');

UPDATE session_slot set session_template_reference = 'nyy.isd.zpz' where session_template_reference = 'pea.uya.sae' and slot_date >= '2024-10-11' and prison_id = (select id from prison where code = 'LHI');

UPDATE session_slot set session_template_reference = 'pyy.cjx.lxz' where session_template_reference = 'eea.hmq.rsa' and slot_date >= '2024-10-11' and prison_id = (select id from prison where code = 'LHI');

UPDATE session_slot set session_template_reference = 'eyy.ubx.esm' where session_template_reference = 'gea.fas.qgo' and slot_date >= '2024-10-11' and prison_id = (select id from prison where code = 'LHI');

UPDATE session_slot set session_template_reference = 'gyy.uep.wzb' where session_template_reference = 'jea.hqp.svd' and slot_date >= '2024-10-11' and prison_id = (select id from prison where code = 'LHI');

UPDATE session_slot set session_template_reference = 'jyy.csm.mvx' where session_template_reference = 'vea.umo.odx' and slot_date >= '2024-10-11' and prison_id = (select id from prison where code = 'LHI');

END;

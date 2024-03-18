-- Fix to update HMP Bullingdon affected future dated visits from old session template to new session template
-- this is down to new session templates created on 2024-03-18 for Bullingdon
--| old session reference | new session reference |
--| wjc.yvp.rbr           | qxl.ubv.dse           |
--| qji.qdx.npj           | axl.feg.jej           |
--| bjc.zsm.gqz           | lxl.fwb.zmb           |
--| ejc.qys.yxx           | yxl.ims.nox           |
--| jjc.gqj.xqs           | mxl.hay.ozm           |

BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'qxl.ubv.dse' where session_template_reference = 'wjc.yvp.rbr' and slot_date >= '2024-03-19' and prison_id = 9;
UPDATE session_slot set session_template_reference = 'axl.feg.jej' where session_template_reference = 'qji.qdx.npj' and slot_date >= '2024-03-19' and prison_id = 9;
UPDATE session_slot set session_template_reference = 'lxl.fwb.zmb' where session_template_reference = 'bjc.zsm.gqz' and slot_date >= '2024-03-19' and prison_id = 9;
UPDATE session_slot set session_template_reference = 'yxl.ims.nox' where session_template_reference = 'ejc.qys.yxx' and slot_date >= '2024-03-19' and prison_id = 9;
UPDATE session_slot set session_template_reference = 'mxl.hay.ozm' where session_template_reference = 'jjc.gqj.xqs' and slot_date >= '2024-03-19' and prison_id = 9;

END;

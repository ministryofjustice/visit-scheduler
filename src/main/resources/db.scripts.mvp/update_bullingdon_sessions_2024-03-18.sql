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

UPDATE session_slot set session_template_reference = 'qxl.ubv.dse' where session_template_reference = 'wjc.yvp.rbr' and slot_date = '2024-03-24' and prison_id = (select id from prison where code = 'BNI');
UPDATE visit v
set session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'qxl.ubv.dse' and ss.slot_date = '2024-03-31' and ss.prison_id = (select id from prison where code = 'BNI'))
where v.session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'wjc.yvp.rbr' and ss.slot_date = '2024-03-31' and ss.prison_id = (select id from prison where code = 'BNI'))
  and v.prison_id = (select id from prison where code = 'BNI');
UPDATE session_slot set session_template_reference = 'qxl.ubv.dse' where session_template_reference = 'wjc.yvp.rbr' and slot_date = '2024-04-07' and prison_id = (select id from prison where code = 'BNI');


UPDATE visit v
set session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'axl.feg.jej' and ss.slot_date = '2024-03-19' and ss.prison_id = (select id from prison where code = 'BNI'))
where v.session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'qji.qdx.npj' and ss.slot_date = '2024-03-19' and ss.prison_id = (select id from prison where code = 'BNI'))
  and v.prison_id = (select id from prison where code = 'BNI');

UPDATE visit v
set session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'axl.feg.jej' and ss.slot_date = '2024-03-26' and ss.prison_id = (select id from prison where code = 'BNI'))
where v.session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'qji.qdx.npj' and ss.slot_date = '2024-03-26' and ss.prison_id = (select id from prison where code = 'BNI'))
  and v.prison_id = (select id from prison where code = 'BNI');

UPDATE visit v
set session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'axl.feg.jej' and ss.slot_date = '2024-04-02' and ss.prison_id = (select id from prison where code = 'BNI'))
where v.session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'qji.qdx.npj' and ss.slot_date = '2024-04-02' and ss.prison_id = (select id from prison where code = 'BNI'))
  and v.prison_id = (select id from prison where code = 'BNI');

UPDATE visit v
set session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'axl.feg.jej' and ss.slot_date = '2024-04-09' and ss.prison_id = (select id from prison where code = 'BNI'))
where v.session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'qji.qdx.npj' and ss.slot_date = '2024-04-09' and ss.prison_id = (select id from prison where code = 'BNI'))
  and v.prison_id = (select id from prison where code = 'BNI');

UPDATE visit v
set session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'lxl.fwb.zmb' and ss.slot_date = '2024-03-20' and ss.prison_id = (select id from prison where code = 'BNI'))
where v.session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'bjc.zsm.gqz' and ss.slot_date = '2024-03-20' and ss.prison_id = (select id from prison where code = 'BNI'))
  and v.prison_id = (select id from prison where code = 'BNI');
UPDATE session_slot set session_template_reference = 'lxl.fwb.zmb' where session_template_reference = 'bjc.zsm.gqz' and slot_date = '2024-03-27' and prison_id = (select id from prison where code = 'BNI');
UPDATE session_slot set session_template_reference = 'lxl.fwb.zmb' where session_template_reference = 'bjc.zsm.gqz' and slot_date = '2024-04-03' and prison_id = (select id from prison where code = 'BNI');
UPDATE session_slot set session_template_reference = 'lxl.fwb.zmb' where session_template_reference = 'bjc.zsm.gqz' and slot_date = '2024-04-10' and prison_id = (select id from prison where code = 'BNI');


UPDATE visit v
set session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'yxl.ims.nox' and ss.slot_date = '2024-03-21' and ss.prison_id = (select id from prison where code = 'BNI'))
where v.session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'ejc.qys.yxx' and ss.slot_date = '2024-03-21' and ss.prison_id = (select id from prison where code = 'BNI'))
  and v.prison_id = (select id from prison where code = 'BNI');
UPDATE session_slot set session_template_reference = 'yxl.ims.nox' where session_template_reference = 'ejc.qys.yxx' and slot_date = '2024-03-28' and prison_id = (select id from prison where code = 'BNI');
UPDATE session_slot set session_template_reference = 'yxl.ims.nox' where session_template_reference = 'ejc.qys.yxx' and slot_date = '2024-04-04' and prison_id = (select id from prison where code = 'BNI');
UPDATE session_slot set session_template_reference = 'yxl.ims.nox' where session_template_reference = 'ejc.qys.yxx' and slot_date = '2024-04-11' and prison_id = (select id from prison where code = 'BNI');

UPDATE session_slot set session_template_reference = 'mxl.hay.ozm' where session_template_reference = 'jjc.gqj.xqs' and slot_date = '2024-03-23' and prison_id = (select id from prison where code = 'BNI');
UPDATE session_slot set session_template_reference = 'mxl.hay.ozm' where session_template_reference = 'jjc.gqj.xqs' and slot_date = '2024-03-30' and prison_id = (select id from prison where code = 'BNI');
UPDATE visit v
set session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'mxl.hay.ozm' and ss.slot_date = '2024-04-06' and ss.prison_id = (select id from prison where code = 'BNI'))
where v.session_slot_id = (select id from session_slot ss where ss.session_template_reference = 'jjc.gqj.xqs' and ss.slot_date = '2024-04-06' and ss.prison_id = (select id from prison where code = 'BNI'))
  and v.prison_id = (select id from prison where code = 'BNI');
UPDATE session_slot set session_template_reference = 'mxl.hay.ozm' where session_template_reference = 'jjc.gqj.xqs' and slot_date = '2024-04-13' and prison_id = (select id from prison where code = 'BNI');

END;

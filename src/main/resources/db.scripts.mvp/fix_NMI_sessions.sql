-- Fix to update Nottingham NMI from old session template to new session template
--| **new session** | **old session** | **day**              |
--| --------------- | --------------- | -------------------- |
--| wyy.uvq.sas     | gzy.cqr.yvg     | Monday AM            |
--| axy.iwr.ppm     | vzy.cnr.gbl     | Tuesday AM           |
--| zxy.ujx.gjn     | wzy.hay.jwv     | Tuesday PM           |
--| lxy.fxp.sse     | zay.fww.nbm     | Wednesday AM         |
--| yxy.ubm.qqe     | kzy.cnr.jvl     | Wednesday PM         |
--| xxy.ubv.vgb     | aay.csz.xgl     | Thursday AM          |
--| mxy.hja.rrm     | lay.cwj.yzq     | Thursday PM          |
--| qxy.ilr.vpv     | may.czb.lrn     | Week 1 - Saturday PM |
--| rxy.hyn.dej     | yay.hse.loe     | Week 2 - Saturday AM |

BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'wyy.uvq.sas' where session_template_reference = 'gzy.cqr.yvg' and prison_id = (select id from prison where code = 'NMI');

UPDATE session_slot set session_template_reference = 'axy.iwr.ppm' where session_template_reference = 'vzy.cnr.gbl' and prison_id = (select id from prison where code = 'NMI');

UPDATE session_slot set session_template_reference = 'zxy.ujx.gjn' where session_template_reference = 'wzy.hay.jwv' and prison_id = (select id from prison where code = 'NMI');

UPDATE session_slot set session_template_reference = 'lxy.fxp.sse' where session_template_reference = 'zay.fww.nbm' and prison_id = (select id from prison where code = 'NMI');

UPDATE session_slot set session_template_reference = 'yxy.ubm.qqe' where session_template_reference = 'kzy.cnr.jvl' and prison_id = (select id from prison where code = 'NMI');

UPDATE session_slot set session_template_reference = 'xxy.ubv.vgb' where session_template_reference = 'aay.csz.xgl' and prison_id = (select id from prison where code = 'NMI');

UPDATE session_slot set session_template_reference = 'mxy.hja.rrm' where session_template_reference = 'lay.cwj.yzq' and prison_id = (select id from prison where code = 'NMI');

UPDATE session_slot set session_template_reference = 'qxy.ilr.vpv' where session_template_reference = 'may.czb.lrn' and prison_id = (select id from prison where code = 'NMI');

UPDATE session_slot set session_template_reference = 'rxy.hyn.dej' where session_template_reference = 'yay.hse.loe' and prison_id = (select id from prison where code = 'NMI');

END;

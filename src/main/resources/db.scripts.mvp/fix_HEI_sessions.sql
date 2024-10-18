-- Fix to update Hewell HEI from old session template to new session template
--| **new session** | **old session** | **day**   |
--| --------------- | --------------- | --------- |
--| qmy.uyr.mmr     | vll.usl.ypo     | Monday    |
--| smy.urq.lwz     | wll.uqj.ymj     | Wednesday |
--| gmy.cvq.qdg     | zyl.ild.evq     | Saturday  |
--| vmy.hvs.oqq     | ayl.ulb.ddx     | Sunday    |

BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'qmy.uyr.mmr' where session_template_reference = 'vll.usl.ypo' and slot_date >= '2024-10-27' and prison_id = (select id from prison where code = 'HEI');

UPDATE session_slot set session_template_reference = 'smy.urq.lwz' where session_template_reference = 'wll.uqj.ymj' and slot_date >= '2024-10-27' and prison_id = (select id from prison where code = 'HEI');

UPDATE session_slot set session_template_reference = 'gmy.cvq.qdg' where session_template_reference = 'zyl.ild.evq' and slot_date >= '2024-10-27' and prison_id = (select id from prison where code = 'HEI');

UPDATE session_slot set session_template_reference = 'vmy.hvs.oqq' where session_template_reference = 'ayl.ulb.ddx' and slot_date >= '2024-10-27' and prison_id = (select id from prison where code = 'HEI');

END;
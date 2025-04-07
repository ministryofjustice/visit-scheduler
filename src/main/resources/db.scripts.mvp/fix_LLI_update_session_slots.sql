-- Fix to update Long Lartin visits from old session templates to new session templates
-- | new session | old session |
-- | ----------- | ----------- |
-- | sqx.fyb.qen | slx.hps.sym |
-- | pqx.fdd.alx | nlx.ids.per |
-- | gqx.ulo.sye | plx.hoy.pqj |
-- | vqx.ipq.snq | elx.hne.mbm |

BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'sqx.fyb.qen' where session_template_reference = 'slx.hps.sym' and prison_id = (select id from prison where code = 'LLI');
UPDATE session_slot set session_template_reference = 'pqx.fdd.alx' where session_template_reference = 'nlx.ids.per' and prison_id = (select id from prison where code = 'LLI');
UPDATE session_slot set session_template_reference = 'gqx.ulo.sye' where session_template_reference = 'plx.hoy.pqj' and prison_id = (select id from prison where code = 'LLI');
UPDATE session_slot set session_template_reference = 'vqx.ipq.snq' where session_template_reference = 'elx.hne.mbm' and prison_id = (select id from prison where code = 'LLI');

END;

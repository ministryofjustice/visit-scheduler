-- Fix to update Nottingham NMI from old session template to new session template
--| **new session** | **old session** | **day**              |
--| --------------- | --------------- | -------------------- |
--| kyy.coq.vwd     | jzy.hdv.mpd     | Monday PM            |

BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'kyy.coq.vwd' where session_template_reference = 'jzy.hdv.mpd' and prison_id = (select id from prison where code = 'NMI');

END;

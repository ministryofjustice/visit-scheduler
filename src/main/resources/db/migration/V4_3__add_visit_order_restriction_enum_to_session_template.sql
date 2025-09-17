BEGIN;

ALTER TABLE session_template ADD COLUMN visit_order_restriction VARCHAR(20);

UPDATE session_template SET visit_order_restriction = 'VO_PVO';

ALTER TABLE session_template ALTER COLUMN visit_order_restriction SET NOT NULL;

COMMIT;
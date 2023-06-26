ALTER TABLE session_template ADD active bool NOT NULL DEFAULT false;

UPDATE session_template	SET active = true;

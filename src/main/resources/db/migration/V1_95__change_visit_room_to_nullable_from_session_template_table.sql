-- In sybase you must drop a column and then recreate it to remove NOT NULL
ALTER TABLE session_template DROP visit_room;
ALTER TABLE session_template ADD  COLUMN visit_room VARCHAR(255);
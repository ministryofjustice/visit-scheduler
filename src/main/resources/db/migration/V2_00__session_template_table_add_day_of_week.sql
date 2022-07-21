-- Need to delete data as day of week is not null
DELETE FROM session_template;
ALTER TABLE session_template ADD day_of_week VARCHAR(40) NOT NULL;

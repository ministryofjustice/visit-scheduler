ALTER TABLE session_template
    RENAME COLUMN start_date TO valid_from_date;

ALTER TABLE session_template
    RENAME COLUMN expiry_date TO valid_to_date;




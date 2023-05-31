BEGIN;

SET SCHEMA 'public';
    INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-06-22' from prison where code = 'DMI';
END;

BEGIN;

    SET SCHEMA 'public';

    UPDATE session_template SET visit_room = 'Visits Main Hall'
        WHERE visit_room = 'Main Visits Hall' AND prison_id = 2;

    UPDATE visit SET visit_room = 'Visits Main Hall'
            WHERE visit_room = 'Main Visits Hall' AND prison_id = 2;

END;

-- MVP data for Hewell visit schedule
-- Requirements set out in VB-819
--
-- Start a transaction
BEGIN;

    SET SCHEMA 'public';

    DELETE FROM session_template WHERE prison_id='HEI';

-- Commit the change
END;
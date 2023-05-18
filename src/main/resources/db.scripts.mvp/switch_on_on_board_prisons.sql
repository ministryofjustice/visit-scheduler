BEGIN;

    SET SCHEMA 'public';

    UPDATE prison SET active = true
        WHERE code in ('LNI','FHI','ESI','DMI','BSI','AGI','FNI');

END;
    CREATE TABLE actioned_by
    (
        id                      serial          NOT NULL PRIMARY KEY,
        booker_reference        text            NULL UNIQUE,
        user_name               varchar(60)     NULL UNIQUE,
        user_type               VARCHAR(80)     NOT NULL,
        create_timestamp        timestamp       NOT NULL default current_timestamp
    );

    CREATE INDEX idx_action_by_user_type ON actioned_by(user_type);

    INSERT INTO actioned_by(user_name,user_type)
            SELECT actioned_by,user_type FROM event_audit
                                         WHERE "type" not in ('PRISON_VISITS_BLOCKED_FOR_DATE','PRISONER_RELEASED_EVENT')
                                         group by actioned_by,user_type;


    -- For Notification as they do not have a source
    INSERT INTO actioned_by(user_type) VALUES ('SYSTEM');

    -- Need to create temp table as the event_audit has six million +  records in it, and updating them takes a very long time
    -- It is faster to create a new table and insert the data and then add indexes at the end.

    CREATE TABLE tmp_event_audit_no_order (
                        id integer NOT NULL PRIMARY KEY,
                        booking_reference text NULL,
                        application_reference text NULL,
                        session_template_reference text NULL,
                        actioned_by_id integer NOT NULL,
                        "type" varchar(90) NOT NULL,
                        application_method_type varchar(90) NOT NULL,
                        "text" text NULL,
                        create_timestamp timestamp NULL DEFAULT CURRENT_TIMESTAMP
    );

    INSERT INTO tmp_event_audit_no_order(id,
                                booking_reference,
                                application_reference,
                                session_template_reference,
                                type,
                                application_method_type,
                                create_timestamp,
                                text,
                                actioned_by_id)
        SELECT ea.id,
               ea.booking_reference,
               ea.application_reference,
               ea.session_template_reference,
               ea.type,
               ea.application_method_type,
               ea.create_timestamp,
               ea.text,
               ab.id as actioned_by_id FROM event_audit ea
            LEFT JOIN  actioned_by ab on ab.user_name = ea.actioned_by and ab.user_type = ea.user_type
            WHERE ea."type" not in ('PRISON_VISITS_BLOCKED_FOR_DATE','PRISONER_RELEASED_EVENT');

    INSERT INTO tmp_event_audit_no_order(id,
                                booking_reference,
                                application_reference,
                                session_template_reference,
                                type,
                                application_method_type,
                                create_timestamp,
                                text,
                                actioned_by_id)
    SELECT ea.id,
           ea.booking_reference,
           ea.application_reference,
           ea.session_template_reference,
           ea.type,
           ea.application_method_type,
           ea.create_timestamp,
           ea.text,
           ab.id as actioned_by_id FROM event_audit ea
                            LEFT JOIN  actioned_by ab on ab.user_type = 'SYSTEM'
    WHERE ea."type" in ('PRISON_VISITS_BLOCKED_FOR_DATE','PRISONER_RELEASED_EVENT');


    CREATE TABLE tmp_event_audit (
                                     id serial NOT NULL PRIMARY KEY,
                                     booking_reference text NULL,
                                     application_reference text NULL,
                                     session_template_reference text NULL,
                                     actioned_by_id integer NOT NULL,
                                     "type" varchar(90) NOT NULL,
                                     application_method_type varchar(90) NOT NULL,
                                     "text" text NULL,
                                     create_timestamp timestamp NULL DEFAULT CURRENT_TIMESTAMP
    );


    INSERT INTO tmp_event_audit(         booking_reference,
                                         application_reference,
                                         session_template_reference,
                                         type,
                                         application_method_type,
                                         create_timestamp,
                                         text,
                                         actioned_by_id)
                    SELECT booking_reference,
                           application_reference,
                           session_template_reference,
                           type,
                           application_method_type,
                           create_timestamp,
                           text,
                           actioned_by_id FROM tmp_event_audit_no_order ea order by id;


    TRUNCATE TABLE tmp_event_audit_no_order;
    DROP TABLE tmp_event_audit_no_order;

    TRUNCATE TABLE event_audit;
    DROP TABLE event_audit;

    ALTER TABLE tmp_event_audit RENAME TO event_audit;
    CREATE INDEX idx_booking_reference ON event_audit(booking_reference);
    CREATE INDEX idx_visit_application_reference ON event_audit(application_reference);
    CREATE INDEX idx_visit_session_template_reference ON event_audit(session_template_reference);
-- Create tables for application refactor
-- Before the application migration is run it may be a good idea to run 'VACUUM FULL;' command!
-- With migrating large amounts of data it's much faster to insert rather than to update.

CREATE TABLE tmp_visit (
                           id        serial NOT NULL PRIMARY KEY,
                           prison_id int4 NOT NULL,
                           prisoner_id varchar(80) NOT NULL,
                           session_slot_id int4 NULL,
                           reference text NOT NULL,
                           application_reference varchar(40) NULL,
                           visit_type varchar(80) NOT NULL,
                           visit_room varchar(255) NOT NULL,
                           visit_status varchar(80) NOT NULL,
                           visit_restriction varchar(80) NOT NULL,
                           outcome_status varchar(40) NULL,
                           created_by varchar(60) NOT NULL DEFAULT 'NOT_KNOWN'::character varying,
                           updated_by varchar(60) NULL,
                           cancelled_by varchar(60) NULL,
                           create_timestamp timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                           modify_timestamp timestamp NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE application (
                             id        serial NOT NULL PRIMARY KEY,
                             prison_id int4 NOT NULL,
                             prisoner_id varchar(80) NOT NULL,
                             session_slot_id int4 NULL,
                             reserved_slot bool NOT NULL DEFAULT true,
                             reference text UNIQUE NULL,
                             booking_reference text NULL,
                             visit_type varchar(80) NULL,
                             restriction varchar(80) NULL,
                             completed bool NOT NULL DEFAULT false,
                             visit_id integer NULL,
                             created_by varchar(60) NULL,
                             create_timestamp timestamp NULL,
                             modify_timestamp timestamp NULL
);



CREATE TABLE session_slot
(
    id                          serial NOT NULL PRIMARY KEY,
    reference               	text            UNIQUE NOT NULL,
    session_template_reference 	text,
    prison_id                   integer         NOT NULL,
    slot_date              		date            NOT NULL,
    slot_start            		timestamp       with time zone NOT NULL,
    slot_end                    timestamp       with time zone NOT NULL
);

CREATE TABLE session_slot_no_order
(
    id                      	serial          NOT NULL PRIMARY KEY,
    session_template_reference 	text,
    prison_id                   integer         NOT NULL,
    slot_date              		date            NOT NULL,
    slot_start            		timestamp       with time zone NOT NULL,
    slot_end                    timestamp       with time zone NOT NULL
);


CREATE TABLE application_contact (
                                     id        serial NOT NULL PRIMARY KEY,
                                     application_id int4 NOT NULL,
                                     contact_name varchar(80) NOT NULL,
                                     contact_phone varchar(40) NOT NULL,
                                     CONSTRAINT application_contact_application_id_key UNIQUE (application_id)
);

CREATE TABLE application_visitor (
                                     id        serial NOT NULL PRIMARY KEY,
                                     application_id int4 NOT NULL,
                                     nomis_person_id int4 NOT NULL,
                                     visit_contact bool DEFAULT false,
                                     CONSTRAINT application_visitor_application_id_nomis_person_id_key UNIQUE (application_id, nomis_person_id)
);

CREATE TABLE application_support (
                                     id        serial NOT NULL PRIMARY KEY,
                                     application_id int4 NOT NULL,
                                     "type" varchar(80) NOT NULL,
                                     "text" text NULL,
                                     CONSTRAINT application_support_application_id_type_key UNIQUE (application_id, type)
);



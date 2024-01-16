-- Before the application migration is run it may be a good idea to run 'VACUUM FULL;' command!

CREATE TABLE tmp_visit (
            id serial4 NOT NULL,
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

-- Create new application table, this is done because updating the existing tmp table will take much long than creating a new table
CREATE TABLE application (
            id int4 NOT NULL,
            prison_id int4 NOT NULL,
            prisoner_id varchar(80) NOT NULL,
            session_slot_id int4 NULL,
            reserved_slot bool NOT NULL DEFAULT true,
            reference text UNIQUE NULL,
            visit_type varchar(80) NULL,
            restriction varchar(80) NULL,
            completed bool NOT NULL DEFAULT false,
            created_by varchar(60) NULL,
            create_timestamp timestamp NULL,
            modify_timestamp timestamp NULL
);

-- Create session slot table
CREATE TABLE session_slot
(
    id                      	serial          NOT NULL PRIMARY KEY,
    reference               	text            UNIQUE NOT NULL,
    session_template_reference 	text,
    prison_id                   integer         NOT NULL,
    slot_date              		date            NOT NULL,
    slot_time              		time            NOT NULL,
    slot_end_time               time            NOT NULL
);

CREATE TABLE application_contact (
              id serial4 NOT NULL,
              application_id int4 NOT NULL,
              contact_name varchar(80) NOT NULL,
              contact_phone varchar(40) NOT NULL,
              CONSTRAINT application_contact_pkey PRIMARY KEY (id),
              CONSTRAINT application_contact_application_id_key UNIQUE (application_id)
);

CREATE TABLE application_visitor (
              id serial4 NOT NULL,
              application_id int4 NOT NULL,
              nomis_person_id int4 NOT NULL,
              visit_contact bool DEFAULT false,
              CONSTRAINT application_visitor_pkey PRIMARY KEY (id),
              CONSTRAINT application_visitor_application_id_nomis_person_id_key UNIQUE (application_id, nomis_person_id)
);

CREATE TABLE application_support (
              id serial4 NOT NULL,
              application_id int4 NOT NULL,
              "type" varchar(80) NOT NULL,
              "text" text NULL,
              CONSTRAINT application_support_pkey PRIMARY KEY (id),
              CONSTRAINT application_support_application_id_type_key UNIQUE (application_id, type)
);


CREATE TABLE visits_to_applications (
                                        visit_id integer NOT NULL,
                                        application_id integer NOT NULL
);


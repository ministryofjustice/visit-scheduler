CREATE TABLE session_incentive_group (
    id serial4 NOT NULL,
    reference text NOT NULL,
    prison_id int4 NOT NULL,
    name varchar(100) NOT NULL,
    create_timestamp timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    modify_timestamp timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT session_incentive_group_pkey PRIMARY KEY (id),
    CONSTRAINT session_incentive_group_reference_key UNIQUE (reference)
);

CREATE TABLE session_to_incentive_group (
    session_template_id int4 NOT NULL,
    session_incentive_group_id int4 NOT NULL,
    CONSTRAINT session_to_incentive_group_pkey PRIMARY KEY (session_template_id, session_incentive_group_id)
);

CREATE TABLE session_prisoner_incentive (
    id serial4 NOT NULL,
    code varchar(100) NOT NULL,
    create_timestamp timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    modify_timestamp timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    session_incentive_group_id int4 NOT NULL,
    CONSTRAINT session_prisoner_incentive_pkey PRIMARY KEY (id)
);

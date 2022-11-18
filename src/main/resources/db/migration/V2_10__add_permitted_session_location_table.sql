CREATE TABLE public.permitted_session_location (
    id  serial NOT NULL PRIMARY KEY,
    prison_id int8 NOT NULL,
	level_one_code varchar(10) NOT NULL,
	level_two_code varchar(10) NULL,
    level_three_code varchar(10) NULL,
	level_four_code varchar(10) NULL,
    create_timestamp timestamp default current_timestamp,
    modify_timestamp timestamp default current_timestamp,
    CONSTRAINT fk_permitted_session_location_to_prison FOREIGN KEY (prison_id) REFERENCES public.prison(id)
);

CREATE TABLE session_to_permitted_location (
         session_template_id int8 NOT NULL,
         permitted_session_location_id int8 NOT NULL,
         CONSTRAINT session_to_permitted_session_location_id_pkey PRIMARY KEY (session_template_id, permitted_session_location_id),
         CONSTRAINT template_must_exist  FOREIGN KEY (session_template_id) REFERENCES session_template(id),
         CONSTRAINT wing_must_exist      FOREIGN KEY (permitted_session_location_id) REFERENCES permitted_session_location(id)
);
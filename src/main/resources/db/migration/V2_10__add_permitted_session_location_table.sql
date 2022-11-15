CREATE TABLE public.permitted_session_location (
    id  serial NOT NULL PRIMARY KEY,
	session_template_id integer NOT NULL,
	level_one_code varchar(50) NOT NULL,
	level_two_code varchar(50) NULL,
    level_three_code varchar(100) NULL,
	level_four_code varchar(255) NULL,
	create_timestamp timestamp with time zone NOT NULL,
	modify_timestamp timestamp with time zone NOT NULL,
	CONSTRAINT fk_permitted_session_location_to_session_template FOREIGN KEY (session_template_id) REFERENCES public.session_template(id)
);
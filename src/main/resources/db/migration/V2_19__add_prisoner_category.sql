CREATE TABLE session_prisoner_category
(
    id                  serial          NOT NULL PRIMARY KEY,
    code                varchar(100)    UNIQUE NOT NULL,
    create_timestamp    timestamp       default current_timestamp
);


CREATE TABLE session_to_included_prisoner_category
(
    session_template_id     integer NOT NULL,
    prisoner_category_id    integer NOT NULL,
    PRIMARY KEY (session_template_id, prisoner_category_id),
    CONSTRAINT inc_template_must_exist FOREIGN KEY (session_template_id) REFERENCES session_template (id),
    CONSTRAINT inc_prisoner_category_must_exist FOREIGN KEY (prisoner_category_id) REFERENCES session_prisoner_category (id)
);

CREATE TABLE session_to_excluded_prisoner_category
(
    session_template_id     integer NOT NULL,
    prisoner_category_id    integer NOT NULL,
    PRIMARY KEY (session_template_id, prisoner_category_id),
    CONSTRAINT excl_template_must_exist FOREIGN KEY (session_template_id) REFERENCES session_template (id),
    CONSTRAINT excl_prisoner_category_must_exist FOREIGN KEY (prisoner_category_id) REFERENCES session_prisoner_category (id)
);
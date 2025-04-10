CREATE TABLE session_template_user_client
(
    id                      serial          NOT NULL PRIMARY KEY,
    session_template_id     integer         NOT NULL,
    user_type               VARCHAR(80)     NOT NULL,
    active                  boolean         NOT NULL DEFAULT true,
    create_timestamp        timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    modify_timestamp        timestamp NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT st_user_client_session_template  FOREIGN KEY (session_template_id) REFERENCES session_template(id)
);


INSERT INTO session_template_user_client (session_template_id, user_type, active) SELECT id,'STAFF', active FROM session_template;
INSERT INTO session_template_user_client (session_template_id, user_type, active) SELECT id,'PUBLIC', active FROM session_template;
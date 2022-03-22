CREATE TABLE visit_support
(
    visit_id        text        NOT NULL,
    support_name    VARCHAR(80) NOT NULL,
    support_details text,
    PRIMARY KEY (visit_id, support_name)
);

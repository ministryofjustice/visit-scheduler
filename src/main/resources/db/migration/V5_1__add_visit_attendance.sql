ALTER TABLE visit
    DROP CONSTRAINT chk_visit_sub_status,
    ADD CONSTRAINT chk_visit_sub_status CHECK (
        (visit_status = 'BOOKED' AND visit_sub_status IN ('AUTO_APPROVED', 'REQUESTED', 'APPROVED'))
        OR (visit_status = 'CANCELLED' AND visit_sub_status IN ('CANCELLED', 'WITHDRAWN', 'AUTO_REJECTED', 'REJECTED'))
        OR (visit_status = 'COMPLETED' AND visit_sub_status IN ('ATTENDED', 'NOT_ATTENDED'))
    );

ALTER TABLE visit_visitor ADD COLUMN attended BOOLEAN;

CREATE TABLE visit_attendance_details
(
    visit_id          BIGINT PRIMARY KEY REFERENCES visit (id),
    search_subject    VARCHAR(255),
    search_type       VARCHAR(255),
    actual_start_time TIME WITHOUT TIME ZONE,
    actual_end_time   TIME WITHOUT TIME ZONE
);

CREATE TABLE visit_notify_history
(
    id                    serial           NOT NULL PRIMARY KEY,
    event_audit_id        integer          NOT NULL,
    notification_id       VARCHAR(36)      NOT NULL,
    notification_type     VARCHAR(6)       NOT NULL,
    status                VARCHAR(20)      NOT NULL,
    template_id            VARCHAR(36)      ,
    template_version       VARCHAR(12)       ,
    sent_at               timestamp,
    completed_at          timestamp,
    created_at            timestamp,

    CONSTRAINT visit_notify_history_event_audit  FOREIGN KEY (event_audit_id) REFERENCES event_audit(id)
);

CREATE INDEX idx_notify_history_notification_id ON visit_notify_history(notification_id);

CREATE TABLE event_audit_attribute (
  id                          SERIAL                   NOT NULL PRIMARY KEY,
  event_audit_id             INTEGER                  NOT NULL,
  attribute_name              VARCHAR(80)              NOT NULL,
  attribute_value             TEXT                     NOT NULL,
  CONSTRAINT fk_event_audit FOREIGN KEY (event_audit_id) REFERENCES event_audit (id) ON DELETE CASCADE
);

CREATE INDEX idx_event_audit_attribute_event_audit_id ON event_audit_attribute(event_audit_id);

-- may be change to serial4
CREATE INDEX idx_application_reference ON application(reference);

-- THESE WERE PREVENTING THE SYSTEM FROM DELETING THE CHILD OBJECTS
-- ALTER TABLE application_contact  ADD CONSTRAINT fk_contact_to_application FOREIGN KEY (application_id) REFERENCES application(id);
-- ALTER TABLE application_support  ADD CONSTRAINT fk_support_to_application FOREIGN KEY (application_id) REFERENCES application(id);
-- ALTER TABLE application_visitor  ADD CONSTRAINT fk_visitor_to_application FOREIGN KEY (application_id) REFERENCES application(id);
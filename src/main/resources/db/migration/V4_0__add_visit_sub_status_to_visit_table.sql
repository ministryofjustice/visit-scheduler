-- Begin work on "Request a visit" Feature. Add new "sub status" column to Visit table
-- to allow us to differentiate between instant bookings and requested bookings.

-- Add sub_status column
-- set DEFAULT to AUTO_APPROVED - this would ensure that we need not run a UPDATE for BOOKED visits as it is taking a long time on PROD

ALTER TABLE visit ADD visit_sub_status VARCHAR(50) DEFAULT 'AUTO_APPROVED';

-- Back fill existing rows with 'CANCELLED' for visit_status = CANCELLED - AUTO_APPROVED for BOOKED should already be set
-- UPDATE visit SET visit_sub_status = 'CANCELLED' WHERE visit_status = 'CANCELLED';
-- finally, drop the default value of AUTO_APPROVED from visit_sub_status
-- ALTER TABLE visit ALTER COLUMN visit_sub_status DROP DEFAULT;

-- Set NOT NULL
-- ALTER TABLE visit ALTER COLUMN visit_sub_status SET NOT NULL;

-- Add CHECK constraint to enforce allowed combinations of visit_status and sub_status
-- ALTER TABLE visit ADD CONSTRAINT chk_visit_sub_status
  --  CHECK (
    --    (visit_status = 'BOOKED' AND visit_sub_status IN ('AUTO_APPROVED', 'REQUESTED', 'APPROVED'))
      --      OR
       -- (visit_status = 'CANCELLED' AND visit_sub_status IN ('CANCELLED', 'WITHDRAWN', 'AUTO_REJECTED', 'REJECTED'))
        -- );

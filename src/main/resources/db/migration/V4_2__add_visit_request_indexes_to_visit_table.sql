-- Index for most common query: BOOKED + REQUESTED
CREATE INDEX IF NOT EXISTS idx_visit_booked_requested
    ON visit (visit_status, visit_sub_status)
    WHERE visit_status = 'BOOKED' AND visit_sub_status = 'REQUESTED';

-- Index for most common query: CANCELLED + REJECTED
CREATE INDEX IF NOT EXISTS idx_visit_cancelled_rejected
    ON visit (visit_status, visit_sub_status)
    WHERE visit_status = 'CANCELLED' AND visit_sub_status = 'REJECTED';

-- To fix bug VB-1537
UPDATE visit SET outcome_status = null
    WHERE visit_status = 'BOOKED' and outcome_status = 'SUPERSEDED_CANCELLATION';
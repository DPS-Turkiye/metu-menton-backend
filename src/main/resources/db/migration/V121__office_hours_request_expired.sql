-- FR-OH-15: a request nobody acted on in time ends as EXPIRED, so its times stop being held.
-- CANCELLED comes from V120 (cancelling a booked meeting); this rewrite keeps it.
ALTER TABLE office_hours_request DROP CONSTRAINT chk_office_hours_request_status;
ALTER TABLE office_hours_request ADD CONSTRAINT chk_office_hours_request_status CHECK (status IN
    ('PENDING', 'ACCEPTED', 'DECLINED', 'MORE_INFO_REQUESTED', 'COUNTER_PROPOSED', 'WITHDRAWN', 'CANCELLED',
     'EXPIRED'));

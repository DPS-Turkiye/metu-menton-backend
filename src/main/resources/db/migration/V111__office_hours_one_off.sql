-- FR-OH-19: office hours on one date, next to the weekly ranges ("Perşembe 14:00–15:00, bir kereye mahsus").
-- Times are instants; a day off still hides them, as it hides the weekly ranges.
CREATE TABLE office_hours_one_off (
    id UUID PRIMARY KEY,
    instructor_id UUID NOT NULL REFERENCES office_hours_user(id) ON DELETE CASCADE,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    place VARCHAR(200),
    CONSTRAINT chk_office_hours_one_off_range CHECK (ends_at > starts_at),
    CONSTRAINT uq_office_hours_one_off_start UNIQUE (instructor_id, starts_at)
);
CREATE INDEX idx_office_hours_one_off_instructor ON office_hours_one_off (instructor_id, starts_at);

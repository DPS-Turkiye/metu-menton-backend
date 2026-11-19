-- Secret links Moodle's calendar subscribes to (FR-OH-23). Moodle fetches the feed from its own server and cannot
-- sign in, so the token in the link is the only key. A row is either one person's feed (their meetings) or one
-- course's feed (its academics' office hours, with no student in it); resetting the link replaces the token.
CREATE TABLE office_hours_calendar_feed (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE REFERENCES office_hours_user(id) ON DELETE CASCADE,
    course_id UUID UNIQUE REFERENCES office_hours_course(id) ON DELETE CASCADE,
    token VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_office_hours_calendar_feed_owner CHECK ((user_id IS NULL) <> (course_id IS NULL))
);

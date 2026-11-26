-- FR-OH-28: what an academician tells students on their page (about, the topics they talk about, this term's
-- courses, expertise tags, how soon they usually answer) and which Office Hours e-mails they want. The lists are
-- ordered as they entered them and saved as a whole. Both e-mails start on, as they were before this column.
ALTER TABLE office_hours_settings ADD COLUMN about VARCHAR(1000);
ALTER TABLE office_hours_settings ADD COLUMN response_days INTEGER;
ALTER TABLE office_hours_settings ADD COLUMN new_request_emails BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE office_hours_settings ADD COLUMN cancellation_emails BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE office_hours_settings ADD CONSTRAINT chk_office_hours_settings_response_days
    CHECK (response_days IS NULL OR response_days BETWEEN 1 AND 14);

CREATE TABLE office_hours_topic (
    instructor_id  UUID NOT NULL REFERENCES office_hours_settings (instructor_id) ON DELETE CASCADE,
    display_order  INTEGER NOT NULL,
    title          VARCHAR(80) NOT NULL,
    body           VARCHAR(300),
    PRIMARY KEY (instructor_id, display_order)
);

CREATE TABLE office_hours_profile_course (
    instructor_id  UUID NOT NULL REFERENCES office_hours_settings (instructor_id) ON DELETE CASCADE,
    display_order  INTEGER NOT NULL,
    code           VARCHAR(20) NOT NULL,
    name           VARCHAR(120) NOT NULL,
    PRIMARY KEY (instructor_id, display_order)
);

CREATE TABLE office_hours_expertise (
    instructor_id  UUID NOT NULL REFERENCES office_hours_settings (instructor_id) ON DELETE CASCADE,
    display_order  INTEGER NOT NULL,
    tag            VARCHAR(40) NOT NULL,
    PRIMARY KEY (instructor_id, display_order)
);

-- Akran Mentoru approval through Prism (weekend launch §C). ODTÜ runs the training video and test on Prism, its
-- service app; the Prism admin sends the people and their scores outside Menton, and a coordinator approves each one
-- (the gate badge, user_badge V95) or asks them to take the test again. The in-app quiz (V97) no longer awards the
-- badge on ODTÜ.

-- The program's Prism link, set by its coordinators in the training editor. https only, checked by the backend.
CREATE TABLE prism_training_link (
    program_id  UUID PRIMARY KEY REFERENCES program (id) ON DELETE CASCADE,
    url         VARCHAR(500) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    updated_by  UUID REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT chk_prism_training_link_https CHECK (url LIKE 'https://%')
);

-- Where a student stands with the Prism training in one program: when they said they finished it, and the
-- coordinator's latest decision. One row per student and program, updated in place. The score is what the Prism admin
-- reported, free text because the test system is not decided yet; only coordinators and admins read it.
CREATE TABLE prism_training_review (
    id            UUID PRIMARY KEY,
    program_id    UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    completed_at  TIMESTAMPTZ,
    decision      VARCHAR(16),
    decided_at    TIMESTAMPTZ,
    decided_by    UUID REFERENCES app_user (id) ON DELETE SET NULL,
    score         VARCHAR(32),
    CONSTRAINT uq_prism_training_review UNIQUE (program_id, user_id),
    CONSTRAINT chk_prism_training_review_decision CHECK (decision IN ('APPROVED', 'RETAKE')),
    CONSTRAINT chk_prism_training_review_decided CHECK ((decision IS NULL) = (decided_at IS NULL))
);

CREATE INDEX ix_prism_training_review_user ON prism_training_review (user_id);
CREATE INDEX ix_prism_training_review_decided_by ON prism_training_review (decided_by);
CREATE INDEX ix_prism_training_link_updated_by ON prism_training_link (updated_by);

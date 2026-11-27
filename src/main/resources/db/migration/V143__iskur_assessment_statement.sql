-- An İŞKUR intern without the Akran Mentoru badge tells the coordinator why they could not finish or pass the
-- assessment (İŞKUR work model, 27 Nov). The coordinator answers with one decision: the intern's weekly mentoring
-- time (kept, set, down to none, or back to the default; written to the placement), optionally a Prism retake, and
-- a note. Unplacing the intern cancels it. At most one waits per intern and program.

CREATE TABLE iskur_assessment_statement (
    id                  UUID PRIMARY KEY,
    program_id          UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    reason              VARCHAR(16) NOT NULL,
    text                VARCHAR(1000) NOT NULL,
    status              VARCHAR(16) NOT NULL,
    mentoring_decision  VARCHAR(8),
    mentoring_minutes   INTEGER,
    retake_opened       BOOLEAN NOT NULL DEFAULT FALSE,
    decision_note       VARCHAR(500),
    decided_by          UUID REFERENCES app_user (id) ON DELETE SET NULL,
    decided_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_iskur_assessment_status CHECK (status IN ('PENDING', 'DECIDED', 'CANCELLED')),
    CONSTRAINT chk_iskur_assessment_reason CHECK (reason IN ('NOT_STARTED', 'FAILED', 'NO_ACCESS', 'PERSONAL',
        'NO_MENTORING', 'OTHER')),
    CONSTRAINT chk_iskur_assessment_text CHECK (length(btrim(text)) > 0),
    CONSTRAINT chk_iskur_assessment_decision CHECK (mentoring_decision IS NULL
        OR mentoring_decision IN ('KEEP', 'SET', 'DEFAULT')),
    CONSTRAINT chk_iskur_assessment_minutes CHECK (mentoring_minutes IS NULL OR mentoring_minutes >= 0)
);

CREATE UNIQUE INDEX uq_iskur_assessment_pending ON iskur_assessment_statement (program_id, user_id)
    WHERE status = 'PENDING';
CREATE INDEX ix_iskur_assessment_program ON iskur_assessment_statement (program_id, created_at DESC);
CREATE INDEX ix_iskur_assessment_user ON iskur_assessment_statement (user_id);
CREATE INDEX ix_iskur_assessment_decided_by ON iskur_assessment_statement (decided_by);

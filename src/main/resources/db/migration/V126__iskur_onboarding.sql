-- İŞKUR onboarding (Figma 284:2, frames 02–04): before placement, each intern says which days and hours they work,
-- which units they would like, how good they are at each unit's skills, which team roles suit them, uploads a CV
-- (and optionally a transcript), and may apply to lead a team. The coordinator places them from these answers.

-- A program's skill catalogue. A skill two units ask about ("Etkinlik organizasyonu") is one row linked to both, so the
-- intern rates it once.
CREATE TABLE iskur_skill (
    id          UUID PRIMARY KEY,
    program_id  UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    name        VARCHAR(80) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_iskur_skill_name UNIQUE (program_id, name)
);

CREATE TABLE iskur_unit_skill (
    id          UUID PRIMARY KEY,
    unit_id     UUID NOT NULL REFERENCES iskur_unit (id) ON DELETE CASCADE,
    skill_id    UUID NOT NULL REFERENCES iskur_skill (id) ON DELETE CASCADE,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_iskur_unit_skill UNIQUE (unit_id, skill_id)
);

CREATE INDEX ix_iskur_unit_skill_skill ON iskur_unit_skill (skill_id);

-- One form per intern per program: a draft until submitted_at is set, then read-only for the intern. The CV and the
-- transcript are stored_document rows of kind iskur-document; deleting the file empties the slot, not the form.
CREATE TABLE iskur_onboarding (
    id                      UUID PRIMARY KEY,
    program_id              UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    user_id                 UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    weekly_days             SMALLINT,
    -- Days already used in earlier İŞKUR programs; a participant may use at most 140 in all.
    prior_iskur_days        SMALLINT,
    cv_document_id          UUID REFERENCES stored_document (id) ON DELETE SET NULL,
    transcript_document_id  UUID REFERENCES stored_document (id) ON DELETE SET NULL,
    wants_leadership        BOOLEAN NOT NULL DEFAULT FALSE,
    leadership_motivation   VARCHAR(500),
    leadership_experience   VARCHAR(500),
    leadership_conflict     VARCHAR(500),
    submitted_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_iskur_onboarding_user UNIQUE (program_id, user_id),
    CONSTRAINT chk_iskur_onboarding_days CHECK (weekly_days IS NULL OR weekly_days BETWEEN 1 AND 3),
    CONSTRAINT chk_iskur_onboarding_prior CHECK (prior_iskur_days IS NULL OR prior_iskur_days BETWEEN 0 AND 140),
    -- Answers to a leadership application nobody is making are not kept.
    CONSTRAINT chk_iskur_onboarding_leadership CHECK (wants_leadership OR (leadership_motivation IS NULL
        AND leadership_experience IS NULL AND leadership_conflict IS NULL)),
    CONSTRAINT chk_iskur_onboarding_submitted CHECK (submitted_at IS NULL
        OR (weekly_days IS NOT NULL AND prior_iskur_days IS NOT NULL))
);

CREATE INDEX ix_iskur_onboarding_user ON iskur_onboarding (user_id);
CREATE INDEX ix_iskur_onboarding_cv ON iskur_onboarding (cv_document_id);
CREATE INDEX ix_iskur_onboarding_transcript ON iskur_onboarding (transcript_document_id);

-- The hours the intern works, as minutes since midnight in Turkish time; ISO day numbers (1 = Monday).
CREATE TABLE iskur_onboarding_range (
    onboarding_id  UUID NOT NULL REFERENCES iskur_onboarding (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    day_of_week    SMALLINT NOT NULL,
    start_minute   SMALLINT NOT NULL,
    end_minute     SMALLINT NOT NULL,
    PRIMARY KEY (onboarding_id, position),
    CONSTRAINT chk_iskur_onboarding_range_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_iskur_onboarding_range_time CHECK (start_minute >= 0 AND end_minute <= 1440
        AND start_minute < end_minute)
);

CREATE TABLE iskur_onboarding_unit (
    onboarding_id  UUID NOT NULL REFERENCES iskur_onboarding (id) ON DELETE CASCADE,
    unit_id        UUID NOT NULL REFERENCES iskur_unit (id) ON DELETE CASCADE,
    PRIMARY KEY (onboarding_id, unit_id)
);

CREATE INDEX ix_iskur_onboarding_unit_unit ON iskur_onboarding_unit (unit_id);

CREATE TABLE iskur_onboarding_skill (
    onboarding_id  UUID NOT NULL REFERENCES iskur_onboarding (id) ON DELETE CASCADE,
    skill_id       UUID NOT NULL REFERENCES iskur_skill (id) ON DELETE CASCADE,
    level          VARCHAR(16) NOT NULL,
    PRIMARY KEY (onboarding_id, skill_id),
    CONSTRAINT chk_iskur_onboarding_skill_level CHECK (level IN ('NONE', 'BEGINNER', 'GOOD', 'EXPERT'))
);

CREATE INDEX ix_iskur_onboarding_skill_skill ON iskur_onboarding_skill (skill_id);

CREATE TABLE iskur_onboarding_team_role (
    onboarding_id  UUID NOT NULL REFERENCES iskur_onboarding (id) ON DELETE CASCADE,
    team_role      VARCHAR(24) NOT NULL,
    PRIMARY KEY (onboarding_id, team_role),
    CONSTRAINT chk_iskur_onboarding_team_role CHECK (team_role IN ('IDEA_GENERATOR', 'PLANNER', 'EXECUTOR',
        'CONNECTOR'))
);

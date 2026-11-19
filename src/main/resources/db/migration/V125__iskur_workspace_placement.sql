-- İŞKUR workspace (Figma 284:2, org chart 274:647): a peer-mentoring program's interns work in units, each unit in
-- thematic teams, and each team has two leaders who each lead a sub-group of the team's members. The interns are the
-- program's own students and peer mentors (app_user.program_id, program_mentor), so no person table is added here.

CREATE TABLE iskur_unit (
    id              UUID PRIMARY KEY,
    program_id      UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    name            VARCHAR(80) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    -- The unit coordinator: one of the program's coordinators.
    coordinator_id  UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_by      UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_iskur_unit_name UNIQUE (program_id, name)
);

CREATE INDEX ix_iskur_unit_coordinator ON iskur_unit (coordinator_id);
CREATE INDEX ix_iskur_unit_created_by ON iskur_unit (created_by);

-- program_id is repeated from the unit so team names are unique across the program: channels are named after teams.
CREATE TABLE iskur_team (
    id          UUID PRIMARY KEY,
    program_id  UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    unit_id     UUID NOT NULL REFERENCES iskur_unit (id) ON DELETE CASCADE,
    name        VARCHAR(80) NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_iskur_team_name UNIQUE (program_id, name)
);

CREATE INDEX ix_iskur_team_unit ON iskur_team (unit_id);

-- One placement per intern per program. The unit comes from the team, so the two cannot disagree. A team that still
-- has people cannot be deleted; the coordinator moves them first. NO ACTION rather than RESTRICT: it is checked at the
-- end of the statement, so deleting a whole program (which cascades to teams and placements) still works.
CREATE TABLE iskur_placement (
    id                 UUID PRIMARY KEY,
    program_id         UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    user_id            UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    team_id            UUID NOT NULL REFERENCES iskur_team (id) ON DELETE NO ACTION,
    team_role          VARCHAR(16) NOT NULL,
    -- A member's leader, whose sub-group they are in; always empty for a leader.
    leader_id          UUID REFERENCES app_user (id) ON DELETE SET NULL,
    status             VARCHAR(16) NOT NULL,
    weekly_days        SMALLINT,
    -- Empty means the default share for weekly_days (about 27 %); 0 means the intern gives no peer mentoring.
    mentoring_minutes  INTEGER,
    placed_by          UUID REFERENCES app_user (id) ON DELETE SET NULL,
    placed_at          TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    finalized_by       UUID REFERENCES app_user (id) ON DELETE SET NULL,
    finalized_at       TIMESTAMPTZ,
    CONSTRAINT uq_iskur_placement_user UNIQUE (program_id, user_id),
    CONSTRAINT chk_iskur_placement_role CHECK (team_role IN ('LEADER', 'MEMBER')),
    CONSTRAINT chk_iskur_placement_status CHECK (status IN ('PROVISIONAL', 'FINAL')),
    CONSTRAINT chk_iskur_placement_leader CHECK (team_role = 'MEMBER' OR leader_id IS NULL),
    CONSTRAINT chk_iskur_placement_not_own_leader CHECK (leader_id IS NULL OR leader_id <> user_id),
    CONSTRAINT chk_iskur_placement_days CHECK (weekly_days IS NULL OR weekly_days BETWEEN 1 AND 3),
    CONSTRAINT chk_iskur_placement_mentoring CHECK (mentoring_minutes IS NULL
        OR (weekly_days IS NOT NULL AND mentoring_minutes BETWEEN 0 AND weekly_days * 450)),
    CONSTRAINT chk_iskur_placement_final CHECK ((status = 'FINAL') = (finalized_at IS NOT NULL))
);

CREATE INDEX ix_iskur_placement_team ON iskur_placement (team_id);
CREATE INDEX ix_iskur_placement_user ON iskur_placement (user_id);
CREATE INDEX ix_iskur_placement_leader ON iskur_placement (leader_id);
CREATE INDEX ix_iskur_placement_placed_by ON iskur_placement (placed_by);
CREATE INDEX ix_iskur_placement_finalized_by ON iskur_placement (finalized_by);

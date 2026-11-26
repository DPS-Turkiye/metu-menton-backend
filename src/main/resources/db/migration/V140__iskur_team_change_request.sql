-- Team change requests during the trial (student handbook p.04, p.12, p.18: "Ekip değiştirmek istersen 4. haftada
-- belirt; kararı koordinatör verir"). The intern writes a short reason; a coordinator moves them, which closes it as
-- MOVED, or declines it with a note. Unplacing the intern cancels it. At most one waits per intern and program.

CREATE TABLE iskur_team_change_request (
    id             UUID PRIMARY KEY,
    program_id     UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    from_team_id   UUID REFERENCES iskur_team (id) ON DELETE SET NULL,
    reason         VARCHAR(500) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    decision_note  VARCHAR(500),
    decided_by     UUID REFERENCES app_user (id) ON DELETE SET NULL,
    decided_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_iskur_team_change_status CHECK (status IN ('PENDING', 'MOVED', 'DECLINED', 'CANCELLED')),
    CONSTRAINT chk_iskur_team_change_reason CHECK (length(btrim(reason)) > 0),
    CONSTRAINT chk_iskur_team_change_declined CHECK (status <> 'DECLINED' OR decision_note IS NOT NULL)
);

CREATE UNIQUE INDEX uq_iskur_team_change_pending ON iskur_team_change_request (program_id, user_id)
    WHERE status = 'PENDING';
CREATE INDEX ix_iskur_team_change_program ON iskur_team_change_request (program_id, created_at DESC);
CREATE INDEX ix_iskur_team_change_user ON iskur_team_change_request (user_id);
CREATE INDEX ix_iskur_team_change_decided_by ON iskur_team_change_request (decided_by);

-- İŞKUR channels (Figma 284:2, rules 322:3413 §4, 5.1, 6.1). They open when a coordinator finalizes a placement and
-- follow every later team change: Genel Duyurular for the whole program, one channel per unit and per thematic team,
-- and a 1:1 between each member and their leader. Coordinators reach the channels through their program role, checked
-- on every request, so only interns are stored as members.

CREATE TABLE iskur_channel (
    id               UUID PRIMARY KEY,
    program_id       UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    kind             VARCHAR(16) NOT NULL,
    unit_id          UUID REFERENCES iskur_unit (id) ON DELETE CASCADE,
    team_id          UUID REFERENCES iskur_team (id) ON DELETE CASCADE,
    -- The two sides of a LEADER_DIRECT channel.
    leader_id        UUID REFERENCES app_user (id) ON DELETE CASCADE,
    member_id        UUID REFERENCES app_user (id) ON DELETE CASCADE,
    created_at       TIMESTAMPTZ NOT NULL,
    last_message_at  TIMESTAMPTZ,
    CONSTRAINT chk_iskur_channel_kind CHECK (kind IN ('ANNOUNCEMENTS', 'UNIT', 'TEAM', 'LEADER_DIRECT')),
    CONSTRAINT chk_iskur_channel_shape CHECK (
        (kind = 'ANNOUNCEMENTS' AND unit_id IS NULL AND team_id IS NULL AND leader_id IS NULL AND member_id IS NULL)
        OR (kind = 'UNIT' AND unit_id IS NOT NULL AND team_id IS NULL AND leader_id IS NULL AND member_id IS NULL)
        OR (kind = 'TEAM' AND team_id IS NOT NULL AND unit_id IS NULL AND leader_id IS NULL AND member_id IS NULL)
        OR (kind = 'LEADER_DIRECT' AND leader_id IS NOT NULL AND member_id IS NOT NULL AND leader_id <> member_id
            AND unit_id IS NULL AND team_id IS NULL)),
    CONSTRAINT uq_iskur_channel_unit UNIQUE (unit_id),
    CONSTRAINT uq_iskur_channel_team UNIQUE (team_id),
    CONSTRAINT uq_iskur_channel_direct UNIQUE (program_id, leader_id, member_id)
);

CREATE UNIQUE INDEX uq_iskur_channel_announcements ON iskur_channel (program_id) WHERE kind = 'ANNOUNCEMENTS';
CREATE INDEX ix_iskur_channel_leader ON iskur_channel (leader_id);
CREATE INDEX ix_iskur_channel_member ON iskur_channel (member_id);

-- An intern in a channel. Leaving (a team move, a new leader, unplacing) sets left_at; the history stays with the
-- channel, and coming back clears it.
CREATE TABLE iskur_channel_member (
    id          UUID PRIMARY KEY,
    channel_id  UUID NOT NULL REFERENCES iskur_channel (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    can_write   BOOLEAN NOT NULL,
    joined_at   TIMESTAMPTZ NOT NULL,
    left_at     TIMESTAMPTZ,
    CONSTRAINT uq_iskur_channel_member UNIQUE (channel_id, user_id)
);

CREATE INDEX ix_iskur_channel_member_user ON iskur_channel_member (user_id);

-- title and important are for Genel Duyurular only; the service enforces that. A sender's account is tombstoned rather
-- than deleted, so SET NULL only matters for a hard delete.
CREATE TABLE iskur_message (
    id          UUID PRIMARY KEY,
    channel_id  UUID NOT NULL REFERENCES iskur_channel (id) ON DELETE CASCADE,
    sender_id   UUID REFERENCES app_user (id) ON DELETE SET NULL,
    title       VARCHAR(160),
    body        VARCHAR(4000) NOT NULL,
    important   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_iskur_message_body CHECK (length(btrim(body)) > 0)
);

-- Pages go back by (created_at, id), and unread counts scan created_at within one channel.
CREATE INDEX ix_iskur_message_channel_created ON iskur_message (channel_id, created_at DESC, id DESC);
CREATE INDEX ix_iskur_message_sender ON iskur_message (sender_id);

-- "Okudum" on an important announcement: an explicit confirmation, separate from opening the channel.
CREATE TABLE iskur_message_receipt (
    id          UUID PRIMARY KEY,
    message_id  UUID NOT NULL REFERENCES iskur_message (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    read_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_iskur_message_receipt UNIQUE (message_id, user_id)
);

CREATE INDEX ix_iskur_message_receipt_user ON iskur_message_receipt (user_id);

-- Each reader's (intern or coordinator) place in a channel. last_read_at is where the unread count starts; empty until
-- they first open it (5.1 "Yeni"). notified_at is the last notification about the channel: another one goes out only
-- once they have read past it, so a busy channel sends one notification, not one per message.
CREATE TABLE iskur_channel_read (
    id            UUID PRIMARY KEY,
    channel_id    UUID NOT NULL REFERENCES iskur_channel (id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    last_read_at  TIMESTAMPTZ,
    notified_at   TIMESTAMPTZ,
    CONSTRAINT uq_iskur_channel_read UNIQUE (channel_id, user_id)
);

CREATE INDEX ix_iskur_channel_read_user ON iskur_channel_read (user_id);

-- A kind of channel someone turned notifications off for (6.1 Bildirimler). Genel Duyurular cannot be muted.
CREATE TABLE iskur_channel_mute (
    id          UUID PRIMARY KEY,
    program_id  UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind        VARCHAR(16) NOT NULL,
    CONSTRAINT chk_iskur_channel_mute_kind CHECK (kind IN ('UNIT', 'TEAM', 'LEADER_DIRECT')),
    CONSTRAINT uq_iskur_channel_mute UNIQUE (program_id, user_id, kind)
);

CREATE INDEX ix_iskur_channel_mute_user ON iskur_channel_mute (user_id);

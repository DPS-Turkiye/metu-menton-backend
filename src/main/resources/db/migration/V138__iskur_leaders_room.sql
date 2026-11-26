-- Liderler odası (student handbook p.17): one channel per unit for its team leaders and the unit's coordinator, where
-- they hold the weekly unit meeting. Like a unit channel it belongs to a unit, so a unit now has up to two channels:
-- the one-channel-per-unit rule becomes one per unit and kind. People can mute it like the other non-announcement
-- channels.

ALTER TABLE iskur_channel DROP CONSTRAINT chk_iskur_channel_kind;
ALTER TABLE iskur_channel ADD CONSTRAINT chk_iskur_channel_kind
    CHECK (kind IN ('ANNOUNCEMENTS', 'UNIT', 'LEADERS', 'TEAM', 'LEADER_DIRECT'));

ALTER TABLE iskur_channel DROP CONSTRAINT chk_iskur_channel_shape;
ALTER TABLE iskur_channel ADD CONSTRAINT chk_iskur_channel_shape CHECK (
    (kind = 'ANNOUNCEMENTS' AND unit_id IS NULL AND team_id IS NULL AND leader_id IS NULL AND member_id IS NULL)
    OR (kind IN ('UNIT', 'LEADERS') AND unit_id IS NOT NULL AND team_id IS NULL AND leader_id IS NULL
        AND member_id IS NULL)
    OR (kind = 'TEAM' AND team_id IS NOT NULL AND unit_id IS NULL AND leader_id IS NULL AND member_id IS NULL)
    OR (kind = 'LEADER_DIRECT' AND leader_id IS NOT NULL AND member_id IS NOT NULL AND leader_id <> member_id
        AND unit_id IS NULL AND team_id IS NULL));

ALTER TABLE iskur_channel DROP CONSTRAINT uq_iskur_channel_unit;
ALTER TABLE iskur_channel ADD CONSTRAINT uq_iskur_channel_unit_kind UNIQUE (unit_id, kind);

ALTER TABLE iskur_channel_mute DROP CONSTRAINT chk_iskur_channel_mute_kind;
ALTER TABLE iskur_channel_mute ADD CONSTRAINT chk_iskur_channel_mute_kind
    CHECK (kind IN ('UNIT', 'LEADERS', 'TEAM', 'LEADER_DIRECT'));

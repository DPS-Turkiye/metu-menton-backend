-- Figma 7.1 "Süre kaydı": besides the meeting itself, a peer mentor records the time spent preparing for it and
-- on the report and follow-up after it. Both count as mentoring minutes in the weekly figures; empty means none.
ALTER TABLE peer_meeting_report ADD COLUMN preparation_minutes INTEGER;
ALTER TABLE peer_meeting_report ADD COLUMN follow_up_minutes INTEGER;
ALTER TABLE peer_meeting_report ADD CONSTRAINT chk_peer_meeting_report_preparation CHECK (
    preparation_minutes IS NULL OR preparation_minutes BETWEEN 0 AND 600);
ALTER TABLE peer_meeting_report ADD CONSTRAINT chk_peer_meeting_report_follow_up CHECK (
    follow_up_minutes IS NULL OR follow_up_minutes BETWEEN 0 AND 600);

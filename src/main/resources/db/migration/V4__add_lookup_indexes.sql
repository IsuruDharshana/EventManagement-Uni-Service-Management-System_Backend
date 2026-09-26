-- Indexes for the lookups the service now runs.
-- GET /api/registrations/mine: registrations of one user.
CREATE INDEX idx_registrations_user_id ON registrations (user_id);

-- Event list: "mine" filter and "published or my own" visibility.
CREATE INDEX idx_events_organizer_id ON events (organizer_id);

-- Auto-complete job: published events whose end time has passed.
CREATE INDEX idx_events_status_schedule_end ON events (status, schedule_end);

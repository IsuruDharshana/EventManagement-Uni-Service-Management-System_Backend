-- Group 5 user ids are strings like 'usr-student-001', not UUIDs.
ALTER TABLE events MODIFY organizer_id VARCHAR(64) NOT NULL;
ALTER TABLE registrations MODIFY user_id VARCHAR(64) NOT NULL;

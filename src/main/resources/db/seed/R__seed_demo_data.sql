-- Repeatable demo/test data. Fixed IDs + ON DUPLICATE KEY UPDATE make it safe to run more than once
-- (and bring older demo rows up to date). User ids follow Group 5's format and match the dev token
-- endpoint (POST /api/dev/token):
--   usr-organizer-001 / usr-organizer-002 (EVENT_ORGANIZER), usr-student-001 .. usr-student-004 (STUDENT)

INSERT INTO events
    (id, title, description, organizer_id, venue, is_online, schedule_start, schedule_end, capacity,
     eligibility_rule, registration_open_at, registration_close_at, status, created_at, updated_at)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Innovation Week Workshop', 'Hands-on workshop for Computing students',
     'usr-organizer-001', 'LAB-101', FALSE,
     DATE_ADD(NOW(6), INTERVAL 7 DAY), DATE_ADD(NOW(6), INTERVAL 7 DAY) + INTERVAL 3 HOUR, 30,
     '{"roles": ["STUDENT"], "departmentId": "dep-cs"}', DATE_SUB(NOW(6), INTERVAL 1 DAY), DATE_ADD(NOW(6), INTERVAL 6 DAY),
     'PUBLISHED', NOW(6), NOW(6)),

    ('a0000000-0000-0000-0000-000000000002', 'Freshers Welcome (Online)', 'Orientation for new students',
     'usr-organizer-001', NULL, TRUE,
     DATE_ADD(NOW(6), INTERVAL 10 DAY), DATE_ADD(NOW(6), INTERVAL 10 DAY) + INTERVAL 2 HOUR, 200,
     '{"all": true}', DATE_SUB(NOW(6), INTERVAL 1 DAY), DATE_ADD(NOW(6), INTERVAL 9 DAY),
     'PUBLISHED', NOW(6), NOW(6)),

    ('a0000000-0000-0000-0000-000000000003', 'Career Fair (draft)', 'Not published yet',
     'usr-organizer-001', 'AUD-MAIN', FALSE,
     DATE_ADD(NOW(6), INTERVAL 20 DAY), DATE_ADD(NOW(6), INTERVAL 20 DAY) + INTERVAL 6 HOUR, 150,
     '{"all": true}', DATE_ADD(NOW(6), INTERVAL 5 DAY), DATE_ADD(NOW(6), INTERVAL 18 DAY),
     'DRAFT', NOW(6), NOW(6)),

    ('a0000000-0000-0000-0000-000000000004', 'Robotics Demo (full)', 'Capacity 2, already full',
     'usr-organizer-002', 'LAB-101', FALSE,
     DATE_ADD(NOW(6), INTERVAL 5 DAY), DATE_ADD(NOW(6), INTERVAL 5 DAY) + INTERVAL 2 HOUR, 2,
     '{"all": true}', DATE_SUB(NOW(6), INTERVAL 1 DAY), DATE_ADD(NOW(6), INTERVAL 4 DAY),
     'PUBLISHED', NOW(6), NOW(6)),

    ('a0000000-0000-0000-0000-000000000005', 'Guest Lecture (cancelled)', 'Cancelled by the organizer',
     'usr-organizer-002', 'AUD-MAIN', FALSE,
     DATE_ADD(NOW(6), INTERVAL 8 DAY), DATE_ADD(NOW(6), INTERVAL 8 DAY) + INTERVAL 1 HOUR, 80,
     '{"all": true}', DATE_SUB(NOW(6), INTERVAL 2 DAY), DATE_ADD(NOW(6), INTERVAL 7 DAY),
     'CANCELLED', NOW(6), NOW(6)),

    ('a0000000-0000-0000-0000-000000000006', 'Hackathon 2026 (completed)', 'Finished event, used for feedback demos',
     'usr-organizer-001', 'AUD-MAIN', FALSE,
     DATE_SUB(NOW(6), INTERVAL 10 DAY), DATE_SUB(NOW(6), INTERVAL 10 DAY) + INTERVAL 8 HOUR, 60,
     '{"all": true}', DATE_SUB(NOW(6), INTERVAL 20 DAY), DATE_SUB(NOW(6), INTERVAL 11 DAY),
     'COMPLETED', NOW(6), NOW(6))
AS seed
ON DUPLICATE KEY UPDATE organizer_id = seed.organizer_id, eligibility_rule = seed.eligibility_rule;

INSERT INTO registrations (id, event_id, user_id, status, created_at, updated_at)
VALUES
    -- Innovation Week: one confirmed, one cancelled
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001',
     'usr-student-001', 'CONFIRMED', NOW(6), NOW(6)),
    ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001',
     'usr-student-002', 'CANCELLED', NOW(6), NOW(6)),
    -- Robotics Demo: both seats taken
    ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000004',
     'usr-student-003', 'CONFIRMED', NOW(6), NOW(6)),
    ('b0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000004',
     'usr-student-004', 'CONFIRMED', NOW(6), NOW(6)),
    -- Hackathon: attended
    ('b0000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000006',
     'usr-student-001', 'CONFIRMED', DATE_SUB(NOW(6), INTERVAL 15 DAY), DATE_SUB(NOW(6), INTERVAL 15 DAY))
AS seed
ON DUPLICATE KEY UPDATE user_id = seed.user_id;

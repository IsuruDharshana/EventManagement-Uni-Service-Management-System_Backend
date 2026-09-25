-- Repeatable demo/test data. Fixed IDs + INSERT IGNORE make it safe to run more than once.
-- Users match the dev token endpoint (POST /api/dev/token):
--   organizer 11111111-..., other organizer 22222222-..., students 33333333-... and 44444444-...

INSERT IGNORE INTO events
    (id, title, description, organizer_id, venue, is_online, schedule_start, schedule_end, capacity,
     eligibility_rule, registration_open_at, registration_close_at, status, created_at, updated_at)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Innovation Week Workshop', 'Hands-on workshop for Computing students',
     '11111111-1111-1111-1111-111111111111', 'LAB-101', FALSE,
     DATE_ADD(NOW(6), INTERVAL 7 DAY), DATE_ADD(NOW(6), INTERVAL 7 DAY) + INTERVAL 3 HOUR, 30,
     '{"department": "Computing"}', DATE_SUB(NOW(6), INTERVAL 1 DAY), DATE_ADD(NOW(6), INTERVAL 6 DAY),
     'PUBLISHED', NOW(6), NOW(6)),

    ('a0000000-0000-0000-0000-000000000002', 'Freshers Welcome (Online)', 'Orientation for new students',
     '11111111-1111-1111-1111-111111111111', NULL, TRUE,
     DATE_ADD(NOW(6), INTERVAL 10 DAY), DATE_ADD(NOW(6), INTERVAL 10 DAY) + INTERVAL 2 HOUR, 200,
     '{"all": true}', DATE_SUB(NOW(6), INTERVAL 1 DAY), DATE_ADD(NOW(6), INTERVAL 9 DAY),
     'PUBLISHED', NOW(6), NOW(6)),

    ('a0000000-0000-0000-0000-000000000003', 'Career Fair (draft)', 'Not published yet',
     '11111111-1111-1111-1111-111111111111', 'AUD-MAIN', FALSE,
     DATE_ADD(NOW(6), INTERVAL 20 DAY), DATE_ADD(NOW(6), INTERVAL 20 DAY) + INTERVAL 6 HOUR, 150,
     '{"all": true}', DATE_ADD(NOW(6), INTERVAL 5 DAY), DATE_ADD(NOW(6), INTERVAL 18 DAY),
     'DRAFT', NOW(6), NOW(6)),

    ('a0000000-0000-0000-0000-000000000004', 'Robotics Demo (full)', 'Capacity 2, already full',
     '22222222-2222-2222-2222-222222222222', 'LAB-101', FALSE,
     DATE_ADD(NOW(6), INTERVAL 5 DAY), DATE_ADD(NOW(6), INTERVAL 5 DAY) + INTERVAL 2 HOUR, 2,
     '{"all": true}', DATE_SUB(NOW(6), INTERVAL 1 DAY), DATE_ADD(NOW(6), INTERVAL 4 DAY),
     'PUBLISHED', NOW(6), NOW(6)),

    ('a0000000-0000-0000-0000-000000000005', 'Guest Lecture (cancelled)', 'Cancelled by the organizer',
     '22222222-2222-2222-2222-222222222222', 'AUD-MAIN', FALSE,
     DATE_ADD(NOW(6), INTERVAL 8 DAY), DATE_ADD(NOW(6), INTERVAL 8 DAY) + INTERVAL 1 HOUR, 80,
     '{"all": true}', DATE_SUB(NOW(6), INTERVAL 2 DAY), DATE_ADD(NOW(6), INTERVAL 7 DAY),
     'CANCELLED', NOW(6), NOW(6)),

    ('a0000000-0000-0000-0000-000000000006', 'Hackathon 2026 (completed)', 'Finished event, used for feedback demos',
     '11111111-1111-1111-1111-111111111111', 'AUD-MAIN', FALSE,
     DATE_SUB(NOW(6), INTERVAL 10 DAY), DATE_SUB(NOW(6), INTERVAL 10 DAY) + INTERVAL 8 HOUR, 60,
     '{"all": true}', DATE_SUB(NOW(6), INTERVAL 20 DAY), DATE_SUB(NOW(6), INTERVAL 11 DAY),
     'COMPLETED', NOW(6), NOW(6));

INSERT IGNORE INTO registrations (id, event_id, user_id, status, created_at, updated_at)
VALUES
    -- Innovation Week: one confirmed, one cancelled
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001',
     '33333333-3333-3333-3333-333333333333', 'CONFIRMED', NOW(6), NOW(6)),
    ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001',
     '44444444-4444-4444-4444-444444444444', 'CANCELLED', NOW(6), NOW(6)),
    -- Robotics Demo: both seats taken
    ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000004',
     'aaaaaaaa-1111-1111-1111-111111111111', 'CONFIRMED', NOW(6), NOW(6)),
    ('b0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000004',
     'bbbbbbbb-1111-1111-1111-111111111111', 'CONFIRMED', NOW(6), NOW(6)),
    -- Hackathon: attended
    ('b0000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000006',
     '33333333-3333-3333-3333-333333333333', 'CONFIRMED', DATE_SUB(NOW(6), INTERVAL 15 DAY), DATE_SUB(NOW(6), INTERVAL 15 DAY));

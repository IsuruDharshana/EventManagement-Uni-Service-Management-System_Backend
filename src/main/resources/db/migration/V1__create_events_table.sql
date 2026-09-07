CREATE TABLE events (
    id                      CHAR(36)      NOT NULL,
    title                   VARCHAR(200)  NOT NULL,
    description             TEXT          NULL,
    organizer_id            CHAR(36)      NOT NULL,
    venue                   VARCHAR(200)  NULL,
    is_online               BOOLEAN       NOT NULL DEFAULT FALSE,
    schedule_start          DATETIME(6)   NOT NULL,
    schedule_end            DATETIME(6)   NOT NULL,
    capacity                INT           NOT NULL,
    eligibility_rule        JSON          NOT NULL,
    registration_open_at    DATETIME(6)   NOT NULL,
    registration_close_at   DATETIME(6)   NOT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_events_capacity_positive CHECK (capacity > 0)
) ENGINE = InnoDB;

CREATE INDEX idx_events_status_schedule_start ON events (status, schedule_start);

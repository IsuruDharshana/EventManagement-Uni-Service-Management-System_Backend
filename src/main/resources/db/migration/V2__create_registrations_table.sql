CREATE TABLE registrations (
    id          CHAR(36)     NOT NULL,
    event_id    CHAR(36)     NOT NULL,
    user_id     CHAR(36)     NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED',
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_registrations_event
        FOREIGN KEY (event_id) REFERENCES events (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_registrations_event_user UNIQUE (event_id, user_id)
) ENGINE = InnoDB;

CREATE INDEX idx_registrations_event_status ON registrations (event_id, status);

CREATE TABLE users (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

CREATE TABLE slots (
    id        UUID         PRIMARY KEY,
    owner_id  UUID         NOT NULL REFERENCES users (id),
    start_at  TIMESTAMPTZ  NOT NULL,
    end_at    TIMESTAMPTZ  NOT NULL,
    status    VARCHAR(16)  NOT NULL,
    version   BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_slots_range CHECK (end_at > start_at),
    CONSTRAINT ck_slots_status CHECK (status IN ('FREE', 'BUSY'))
);

CREATE INDEX idx_slots_owner_start_end ON slots (owner_id, start_at, end_at);

CREATE TABLE meetings (
    id            UUID            PRIMARY KEY,
    title         VARCHAR(255)    NOT NULL,
    description   VARCHAR(2048),
    organizer_id  UUID            NOT NULL REFERENCES users (id),
    slot_id       UUID            NOT NULL UNIQUE REFERENCES slots (id),
    created_at    TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_meetings_organizer ON meetings (organizer_id);

CREATE TABLE meeting_participants (
    id           UUID         PRIMARY KEY,
    meeting_id   UUID         NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    display_name VARCHAR(255) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    user_id      UUID         REFERENCES users (id),
    CONSTRAINT uq_participant_meeting_email UNIQUE (meeting_id, email)
);

CREATE INDEX idx_participants_email ON meeting_participants (email);

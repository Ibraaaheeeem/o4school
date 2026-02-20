-- Create Internal Messaging Tables

CREATE TABLE internal_message_threads (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    subject VARCHAR(255) NOT NULL,
    last_message_preview VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_internal_thread_school_created ON internal_message_threads(school_id, created_at);

CREATE TABLE internal_message_participants (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    thread_id UUID NOT NULL REFERENCES internal_message_threads(id),
    user_id UUID NOT NULL REFERENCES users(id),
    unread_count INTEGER NOT NULL DEFAULT 0,
    last_read_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT unique_participant_thread_user UNIQUE (thread_id, user_id)
);

CREATE INDEX idx_internal_participant_user_unread ON internal_message_participants(user_id, unread_count);

CREATE TABLE internal_messages (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    thread_id UUID NOT NULL REFERENCES internal_message_threads(id),
    sender_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_internal_message_thread_created ON internal_messages(thread_id, created_at);

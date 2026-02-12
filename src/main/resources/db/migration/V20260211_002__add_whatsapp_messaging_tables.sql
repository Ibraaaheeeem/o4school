CREATE TABLE IF NOT EXISTS whatsapp_messages (
    id UUID PRIMARY KEY,
    recipient_phone VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    direction VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    meta_message_id VARCHAR(255),
    user_id UUID REFERENCES users(id),
    school_id UUID REFERENCES schools(id),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_whatsapp_messages_phone ON whatsapp_messages(recipient_phone);
CREATE INDEX idx_whatsapp_messages_school ON whatsapp_messages(school_id);

CREATE TABLE IF NOT EXISTS fee_reminder_schedules (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL REFERENCES schools(id),
    frequency VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fee_reminder_school ON fee_reminder_schedules(school_id);

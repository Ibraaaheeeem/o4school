-- Create school_subscriptions table
CREATE TABLE school_subscriptions (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    fee_collection_active BOOLEAN NOT NULL DEFAULT FALSE,
    whatsapp_balance INTEGER NOT NULL DEFAULT 0,
    sms_balance INTEGER NOT NULL DEFAULT 0,
    ai_token_balance INTEGER NOT NULL DEFAULT 0,
    last_updated TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT unique_school_subscription UNIQUE(school_id)
);

-- Create service_usage_logs table
CREATE TABLE service_usage_logs (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    service_type VARCHAR(255) NOT NULL,
    amount INTEGER NOT NULL DEFAULT 1,
    description TEXT,
    timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_usage_log_school ON service_usage_logs(school_id);
CREATE INDEX idx_usage_log_user ON service_usage_logs(user_id);
CREATE INDEX idx_usage_log_service ON service_usage_logs(service_type);
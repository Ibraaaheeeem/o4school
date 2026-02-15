CREATE TABLE IF NOT EXISTS whatsapp_templates (
    id UUID PRIMARY KEY,
    template_id VARCHAR(255) NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    language VARCHAR(50) NOT NULL,
    category VARCHAR(100) NOT NULL,
    parameter_count INTEGER NOT NULL DEFAULT 0,
    parameter_mapping TEXT, -- JSON mapping of {{1}} -> friendly_name
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    school_id UUID REFERENCES schools(id),
    last_synced_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_templates_school ON whatsapp_templates(school_id);
CREATE INDEX IF NOT EXISTS idx_whatsapp_templates_name ON whatsapp_templates(template_name);
CREATE UNIQUE INDEX IF NOT EXISTS idx_whatsapp_templates_meta_id ON whatsapp_templates(template_id);

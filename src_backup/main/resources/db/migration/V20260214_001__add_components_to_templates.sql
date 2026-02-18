-- Add components_json column to whatsapp_templates table
ALTER TABLE whatsapp_templates 
ADD COLUMN IF NOT EXISTS components_json TEXT;

COMMENT ON COLUMN whatsapp_templates.components_json IS 'Store the structural components of the WhatsApp template as JSON';

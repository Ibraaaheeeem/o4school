ALTER TABLE whatsapp_templates ADD COLUMN target_role VARCHAR(20) DEFAULT 'GENERAL';

UPDATE whatsapp_templates 
SET target_role = 'PARENT' 
WHERE template_name IN ('bill_reminder', 'payment_received', 'payment_request');

ALTER TABLE whatsapp_templates ADD COLUMN is_for_broadcast BOOLEAN DEFAULT FALSE;

UPDATE whatsapp_templates 
SET is_for_broadcast = TRUE 
WHERE template_name IN ('welcome_message', 'bill_reminder', 'payment_request', 'announcement_for_action', 'important_announcement', 'announcement');

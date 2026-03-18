-- Remove school_id column to make templates global across all schools
ALTER TABLE whatsapp_templates DROP COLUMN school_id CASCADE;

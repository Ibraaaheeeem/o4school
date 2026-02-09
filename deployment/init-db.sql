-- Create additional databases if they don't exist
SELECT 'CREATE DATABASE elearner'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'elearner')\gexec

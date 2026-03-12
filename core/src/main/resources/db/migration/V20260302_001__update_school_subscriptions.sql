CREATE TYPE subscription_status_enum AS ENUM ('ACTIVE', 'EXPIRED', 'GRACE_PERIOD');

ALTER TABLE school_subscriptions 
ADD COLUMN subscription_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
ADD COLUMN valid_until TIMESTAMP;

-- For existing schools, provide a 4 month grace period from their creation date
UPDATE school_subscriptions ss
SET valid_until = (
    SELECT created_at + INTERVAL '4 months'
    FROM schools s
    WHERE s.id = ss.school_id
)
WHERE valid_until IS NULL;

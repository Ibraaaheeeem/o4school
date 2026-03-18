-- Add parent_id to settlements table
ALTER TABLE settlements ADD COLUMN parent_id UUID;

-- Add foreign key constraint
ALTER TABLE settlements ADD CONSTRAINT fk_settlements_parent 
FOREIGN KEY (parent_id) REFERENCES parents(id);

-- Add index for performance
CREATE INDEX idx_settlement_parent ON settlements(parent_id);
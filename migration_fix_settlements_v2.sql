-- Drop the not null constraint on wallet_id since we now use paystack_wallet_id and squad_wallet_id
ALTER TABLE settlements ALTER COLUMN wallet_id DROP NOT NULL;

-- Drop the existing check constraint for settlement_type
ALTER TABLE settlements DROP CONSTRAINT IF EXISTS settlements_settlement_type_check;

-- Update existing AUTO settlements to MANUAL to satisfy the new constraint
UPDATE settlements SET settlement_type = 'MANUAL' WHERE settlement_type = 'AUTO';

-- Add the new check constraint including PAYSTACK and SQUAD
ALTER TABLE settlements ADD CONSTRAINT settlements_settlement_type_check 
    CHECK (settlement_type IN ('PAYSTACK', 'SQUAD', 'MANUAL'));

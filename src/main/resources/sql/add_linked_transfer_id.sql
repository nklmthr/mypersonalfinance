-- Migration script to add linked_transfer_id column for bidirectional transfer linking
-- This column allows us to maintain referential integrity between transfer transaction pairs

ALTER TABLE account_transactions 
ADD COLUMN linked_transfer_id VARCHAR(255) NULL;

-- Add index for better query performance
CREATE INDEX idx_account_transactions_linked_transfer_id 
ON account_transactions(linked_transfer_id);

-- Add comment for documentation
COMMENT ON COLUMN account_transactions.linked_transfer_id IS 
'References the ID of the paired transaction in a transfer. For transfers, debit transaction links to credit and vice versa.';


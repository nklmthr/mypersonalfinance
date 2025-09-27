-- Add account identifier columns for better transaction text matching
-- Run this migration to add the new identifier fields to the accounts table

ALTER TABLE accounts 
ADD COLUMN account_number VARCHAR(100),
ADD COLUMN account_keywords VARCHAR(500) COMMENT 'Comma-separated keywords that might appear in transaction text',
ADD COLUMN account_aliases VARCHAR(300) COMMENT 'Comma-separated alternative names/abbreviations';

-- Add gpt_account_id column to account_transactions table to store GPT fuzzy match results
ALTER TABLE account_transactions 
ADD COLUMN gpt_account_id VARCHAR(36) COMMENT 'Foreign key to accounts table for GPT-matched account';

-- Add foreign key constraint (optional, uncomment if needed)
-- ALTER TABLE account_transactions 
-- ADD CONSTRAINT fk_gpt_account 
-- FOREIGN KEY (gpt_account_id) REFERENCES accounts(id);

-- Example data for demonstration (adjust based on your actual accounts)
-- UPDATE accounts SET 
--   account_number = 'XXXX1234', 
--   account_keywords = 'hdfc,bank,savings,1234',
--   account_aliases = 'HDFC SAV,HDFC Savings,SAV A/C'
-- WHERE name = 'HDFC Savings Account';

-- UPDATE accounts SET 
--   account_number = 'XXXX5678', 
--   account_keywords = 'icici,credit,card,5678',
--   account_aliases = 'ICICI CC,ICICI Credit,CC'
-- WHERE name = 'ICICI Credit Card';

-- Note: gpt_account_id field in account_transactions will be automatically populated by the OpenAI client
-- when processing transactions. It stores a reference to the account that was matched through GPT fuzzy matching,
-- allowing both the original account (from data extraction services) and the GPT-matched account
-- to be preserved for comparison and analysis.

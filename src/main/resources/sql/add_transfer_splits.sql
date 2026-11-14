CREATE TABLE IF NOT EXISTS transfer_splits (
    id                    VARCHAR(36)   NOT NULL,
    source_transaction_id VARCHAR(36)   NOT NULL,
    dest_transaction_id   VARCHAR(36)   NOT NULL,
    amount                DECIMAL(19,4) NOT NULL,
    explanation           VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ts_source FOREIGN KEY (source_transaction_id) REFERENCES account_transactions(id),
    CONSTRAINT fk_ts_dest   FOREIGN KEY (dest_transaction_id)   REFERENCES account_transactions(id)
);

CREATE INDEX IF NOT EXISTS idx_ts_source ON transfer_splits(source_transaction_id);
CREATE INDEX IF NOT EXISTS idx_ts_dest   ON transfer_splits(dest_transaction_id);

-- Backfill existing 1-to-1 transfers.
-- Each pair appears TWICE in account_transactions (A.linkedTransferId=B.id AND B.linkedTransferId=A.id).
-- Insert only the DEBIT->CREDIT direction to avoid duplicates.
INSERT INTO transfer_splits (id, source_transaction_id, dest_transaction_id, amount, explanation)
SELECT
    UUID(),
    debit.id,
    credit.id,
    debit.amount,
    credit.explanation
FROM account_transactions debit
JOIN account_transactions credit
    ON debit.linked_transfer_id = credit.id
   AND credit.linked_transfer_id = debit.id
WHERE debit.type  = 'DEBIT'
  AND credit.type = 'CREDIT';

-- After verifying the backfill is correct, run these to clean up the old column:
-- UPDATE account_transactions SET linked_transfer_id = NULL
-- WHERE linked_transfer_id IS NOT NULL AND type = 'DEBIT'
--   AND EXISTS (SELECT 1 FROM transfer_splits ts WHERE ts.source_transaction_id = account_transactions.id);
-- UPDATE account_transactions SET linked_transfer_id = NULL
-- WHERE linked_transfer_id IS NOT NULL AND type = 'CREDIT'
--   AND EXISTS (SELECT 1 FROM transfer_splits ts WHERE ts.dest_transaction_id = account_transactions.id);

-- Gecmise donuk sozlu borclar odeme plani satiri olarak izlenebilir.
ALTER TABLE installment DROP CONSTRAINT installment_method_check;
ALTER TABLE installment ADD CONSTRAINT installment_method_check
    CHECK (method IN ('CASH', 'TRANSFER', 'PROMISSORY_NOTE', 'INSTITUTION', 'CHEQUE', 'OPEN_ACCOUNT'));

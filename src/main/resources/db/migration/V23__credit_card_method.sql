-- Kredi karti odeme yontemi (satis ve alis planinda; tahsilat genelde bankaya). Veri degismez, sadece kisit genisler.
ALTER TABLE installment DROP CONSTRAINT installment_method_check;
ALTER TABLE installment ADD CONSTRAINT installment_method_check
    CHECK (method IN ('CASH', 'TRANSFER', 'PROMISSORY_NOTE', 'INSTITUTION', 'CHEQUE', 'OPEN_ACCOUNT', 'CREDIT_CARD'));

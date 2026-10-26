ALTER TABLE tenant ADD COLUMN reminder_time TIME NOT NULL DEFAULT '09:00';
ALTER TABLE tenant ADD COLUMN reminder_sent_on DATE;

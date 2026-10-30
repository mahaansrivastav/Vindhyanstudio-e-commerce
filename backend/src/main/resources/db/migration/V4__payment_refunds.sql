-- Refunds issued through the payment gateway when a paid order is cancelled.
ALTER TABLE payments ADD COLUMN refund_id VARCHAR(100);
ALTER TABLE payments ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

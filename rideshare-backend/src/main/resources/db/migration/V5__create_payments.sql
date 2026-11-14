CREATE TABLE payments (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id             UUID           NOT NULL REFERENCES bookings (id),
    payer_id               UUID           NOT NULL REFERENCES users (id),
    amount                 NUMERIC(10, 2) NOT NULL,
    payment_method         VARCHAR(20)    NOT NULL,
    payment_status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    transaction_reference  VARCHAR(64)    NOT NULL,
    provider               VARCHAR(30)    NOT NULL,
    provider_reference     VARCHAR(100),
    failure_reason         VARCHAR(500),
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_payments_transaction_reference UNIQUE (transaction_reference),
    CONSTRAINT ck_payments_method CHECK (payment_method IN ('CASH', 'UPI', 'CARD', 'ONLINE')),
    CONSTRAINT ck_payments_status CHECK (payment_status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0)
);

-- At most one in-flight or successful payment per booking.
CREATE UNIQUE INDEX uk_payments_live_per_booking
    ON payments (booking_id) WHERE payment_status IN ('PENDING', 'SUCCESS');
CREATE INDEX idx_payments_payer ON payments (payer_id, created_at DESC);

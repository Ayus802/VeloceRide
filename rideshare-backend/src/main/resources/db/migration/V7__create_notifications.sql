CREATE TABLE notifications (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id       UUID          NOT NULL REFERENCES users (id),
    title              VARCHAR(150)  NOT NULL,
    message            VARCHAR(1000) NOT NULL,
    notification_type  VARCHAR(30)   NOT NULL,
    reference_id       UUID,
    is_read            BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_notifications_type CHECK (notification_type IN (
        'BOOKING_REQUEST', 'BOOKING_ACCEPTED', 'BOOKING_REJECTED', 'BOOKING_CANCELLED',
        'RIDE_STARTED', 'RIDE_COMPLETED', 'RIDE_CANCELLED', 'PAYMENT_SUCCESS', 'PAYMENT_FAILED',
        'NEW_REVIEW', 'SYSTEM'))
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, is_read, created_at DESC);

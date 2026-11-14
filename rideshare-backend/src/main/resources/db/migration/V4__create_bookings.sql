CREATE TABLE bookings (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id            UUID           NOT NULL REFERENCES rides (id),
    passenger_id       UUID           NOT NULL REFERENCES users (id),
    number_of_seats    INTEGER        NOT NULL,
    total_amount       NUMERIC(10, 2) NOT NULL,
    booking_status     VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    booking_reference  VARCHAR(20)    NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_bookings_reference UNIQUE (booking_reference),
    CONSTRAINT ck_bookings_status CHECK (booking_status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_bookings_seats CHECK (number_of_seats >= 1),
    CONSTRAINT ck_bookings_amount CHECK (total_amount >= 0)
);

-- A passenger may hold at most one live (pending or accepted) booking per ride.
CREATE UNIQUE INDEX uk_bookings_active_per_passenger
    ON bookings (ride_id, passenger_id) WHERE booking_status IN ('PENDING', 'ACCEPTED');
CREATE INDEX idx_bookings_ride_status ON bookings (ride_id, booking_status);
CREATE INDEX idx_bookings_passenger ON bookings (passenger_id, created_at DESC);

CREATE TABLE rides (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id               UUID           NOT NULL REFERENCES users (id),
    vehicle_id              UUID           NOT NULL REFERENCES vehicles (id),
    source                  VARCHAR(200)   NOT NULL,
    destination             VARCHAR(200)   NOT NULL,
    source_latitude         DOUBLE PRECISION,
    source_longitude        DOUBLE PRECISION,
    destination_latitude    DOUBLE PRECISION,
    destination_longitude   DOUBLE PRECISION,
    departure_date          DATE           NOT NULL,
    departure_time          TIME           NOT NULL,
    estimated_arrival_time  TIMESTAMP,
    total_seats             INTEGER        NOT NULL,
    available_seats         INTEGER        NOT NULL,
    price_per_seat          NUMERIC(10, 2) NOT NULL,
    description             VARCHAR(1000),
    ride_status             VARCHAR(20)    NOT NULL DEFAULT 'SCHEDULED',
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_rides_status CHECK (ride_status IN ('SCHEDULED', 'ONGOING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_rides_total_seats CHECK (total_seats BETWEEN 1 AND 20),
    -- Backstop against overbooking: seats can never go negative or exceed the total.
    CONSTRAINT ck_rides_available_seats CHECK (available_seats >= 0 AND available_seats <= total_seats),
    CONSTRAINT ck_rides_price CHECK (price_per_seat >= 0),
    CONSTRAINT ck_rides_source_lat CHECK (source_latitude IS NULL OR source_latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_rides_source_lng CHECK (source_longitude IS NULL OR source_longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_rides_dest_lat CHECK (destination_latitude IS NULL OR destination_latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_rides_dest_lng CHECK (destination_longitude IS NULL OR destination_longitude BETWEEN -180 AND 180)
);

-- Search: date + status first (highly selective), then case-insensitive locations.
CREATE INDEX idx_rides_search ON rides (departure_date, ride_status, lower(source), lower(destination));
CREATE INDEX idx_rides_driver ON rides (driver_id, departure_date DESC);
CREATE INDEX idx_rides_vehicle ON rides (vehicle_id);

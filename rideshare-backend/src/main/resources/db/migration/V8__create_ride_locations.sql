CREATE TABLE ride_locations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id      UUID             NOT NULL REFERENCES rides (id),
    driver_id    UUID             NOT NULL REFERENCES users (id),
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    recorded_at  TIMESTAMPTZ      NOT NULL,
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT ck_ride_locations_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_ride_locations_lng CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_ride_locations_ride_time ON ride_locations (ride_id, recorded_at DESC);

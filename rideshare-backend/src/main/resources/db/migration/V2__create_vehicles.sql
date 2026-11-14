CREATE TABLE vehicles (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id                   UUID          NOT NULL REFERENCES users (id),
    vehicle_name               VARCHAR(100)  NOT NULL,
    vehicle_number             VARCHAR(20)   NOT NULL,
    vehicle_type               VARCHAR(20)   NOT NULL,
    brand                      VARCHAR(50)   NOT NULL,
    model                      VARCHAR(50)   NOT NULL,
    color                      VARCHAR(30)   NOT NULL,
    seating_capacity           INTEGER       NOT NULL,
    registration_document_url  VARCHAR(1000),
    verification_status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    rejection_reason           VARCHAR(500),
    deleted                    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_vehicles_type CHECK (vehicle_type IN ('HATCHBACK', 'SEDAN', 'SUV', 'MUV', 'VAN', 'OTHER')),
    CONSTRAINT ck_vehicles_seating_capacity CHECK (seating_capacity BETWEEN 1 AND 20),
    CONSTRAINT ck_vehicles_verification_status CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

-- A registration number may only be in use by one non-deleted vehicle.
CREATE UNIQUE INDEX uk_vehicles_vehicle_number_active ON vehicles (vehicle_number) WHERE deleted = FALSE;
CREATE INDEX idx_vehicles_owner ON vehicles (owner_id) WHERE deleted = FALSE;
CREATE INDEX idx_vehicles_verification_status ON vehicles (verification_status) WHERE deleted = FALSE;

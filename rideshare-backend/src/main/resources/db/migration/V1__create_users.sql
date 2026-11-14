-- One users table for everyone: the same account can drive (RIDER) and book (PASSENGER).
CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name        VARCHAR(100)  NOT NULL,
    email            VARCHAR(255)  NOT NULL,
    phone_number     VARCHAR(20)   NOT NULL,
    password         VARCHAR(100)  NOT NULL,
    profile_picture  VARCHAR(1000),
    active_mode      VARCHAR(20)   NOT NULL DEFAULT 'PASSENGER',
    account_status   VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    role             VARCHAR(20)   NOT NULL DEFAULT 'USER',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone_number UNIQUE (phone_number),
    CONSTRAINT ck_users_active_mode CHECK (active_mode IN ('RIDER', 'PASSENGER')),
    CONSTRAINT ck_users_account_status CHECK (account_status IN ('ACTIVE', 'DEACTIVATED')),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE INDEX idx_users_created_at ON users (created_at);

-- Email/phone OTP verification and Google sign-in.

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT FALSE;
-- Google account id ("sub" claim) for accounts that signed in with Google.
ALTER TABLE users ADD COLUMN google_subject VARCHAR(64);
-- Google-only accounts have no password until they set one via "forgot password".
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

CREATE UNIQUE INDEX uk_users_google_subject ON users (google_subject) WHERE google_subject IS NOT NULL;

-- Accounts that existed before verification was introduced are trusted as-is, so nobody is locked out.
UPDATE users SET email_verified = TRUE, phone_verified = TRUE;

-- One-time codes. Only a SHA-256 hash of the code is stored.
CREATE TABLE verification_codes (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    channel      VARCHAR(10)  NOT NULL,
    destination  VARCHAR(255) NOT NULL,
    code_hash    VARCHAR(64)  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_verification_codes_channel CHECK (channel IN ('EMAIL', 'PHONE'))
);

CREATE INDEX idx_verification_codes_user_channel ON verification_codes (user_id, channel, created_at DESC);

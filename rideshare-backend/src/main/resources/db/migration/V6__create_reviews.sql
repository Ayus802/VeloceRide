CREATE TABLE reviews (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id           UUID          NOT NULL REFERENCES rides (id),
    reviewer_id       UUID          NOT NULL REFERENCES users (id),
    reviewed_user_id  UUID          NOT NULL REFERENCES users (id),
    rating            INTEGER       NOT NULL,
    comment           VARCHAR(1000),
    hidden            BOOLEAN       NOT NULL DEFAULT FALSE,
    moderation_note   VARCHAR(500),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_reviews_ride_reviewer_reviewed UNIQUE (ride_id, reviewer_id, reviewed_user_id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_not_self CHECK (reviewer_id <> reviewed_user_id)
);

-- Covers the AVG/COUNT aggregate used for user ratings.
CREATE INDEX idx_reviews_reviewed_user ON reviews (reviewed_user_id, hidden, rating);
CREATE INDEX idx_reviews_ride ON reviews (ride_id);

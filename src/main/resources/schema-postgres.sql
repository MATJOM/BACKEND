-- Users
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL,
    name TEXT NOT NULL,
    password TEXT,
    provider VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_user_email_provider UNIQUE (email, provider),
    CONSTRAINT chk_password_required CHECK (
        (provider = 'LOCAL' AND password IS NOT NULL) OR
        (provider <> 'LOCAL' AND password IS NULL)
    )
);

-- Places
CREATE TABLE IF NOT EXISTS places (
    place_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    lat NUMERIC(9,6) NOT NULL,
    lng NUMERIC(9,6) NOT NULL,
    category TEXT[] NOT NULL,
    provider_id VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    working_hours JSONB NOT NULL,
    break_time JSONB NOT NULL,
    opened_at DATE NOT NULL,
    opened_at_source JSONB NOT NULL,
    biz_status VARCHAR(20) NOT NULL,
    addr_sido VARCHAR(20) NOT NULL,
    addr_sigungu VARCHAR(30) NOT NULL,
    addr_eupmyeondong VARCHAR(80) NOT NULL,
    addr_street VARCHAR(100) NOT NULL,
    addr_detail VARCHAR(100) NOT NULL,
    location geography(Point,4326),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_places_provider_id UNIQUE (provider_id)
);

CREATE INDEX IF NOT EXISTS idx_places_lower_name ON places (lower(name));
CREATE INDEX IF NOT EXISTS idx_places_category ON places USING GIN (category);
CREATE INDEX IF NOT EXISTS idx_places_location ON places USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_places_sido ON places (addr_sido);
CREATE INDEX IF NOT EXISTS idx_places_sigungu ON places (addr_sigungu);
CREATE INDEX IF NOT EXISTS idx_places_eupmyeondong ON places (addr_eupmyeondong);

-- Visits
CREATE TABLE IF NOT EXISTS visits (
    visit_id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    place_id BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    client_mode VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    arrived_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    expired_at TIMESTAMPTZ,
    last_pos_at TIMESTAMPTZ,
    dwell_started_at TIMESTAMPTZ,
    last_lat NUMERIC(9,6),
    last_lng NUMERIC(9,6),
    last_accuracy_m NUMERIC(6,2),
    meta JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_visits_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_visits_place FOREIGN KEY (place_id) REFERENCES places(place_id),
    CONSTRAINT chk_visit_state CHECK (state IN ('ACTIVE', 'ARRIVED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_visit_client_mode CHECK (client_mode IN ('NAVIGATION', 'IDLE'))
);

CREATE INDEX IF NOT EXISTS idx_visits_user ON visits (user_id);
CREATE INDEX IF NOT EXISTS idx_visits_place ON visits (place_id);
CREATE INDEX IF NOT EXISTS idx_visits_started_at ON visits (started_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_visits_active_user ON visits (user_id) WHERE state = 'ACTIVE';

-- Visit positions
CREATE TABLE IF NOT EXISTS visit_positions (
    pos_id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL,
    lat NUMERIC(9,6) NOT NULL,
    lng NUMERIC(9,6) NOT NULL,
    accuracy_m NUMERIC(6,2),
    mode VARCHAR(20) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_visit_positions_visit FOREIGN KEY (visit_id) REFERENCES visits(visit_id),
    CONSTRAINT chk_visit_position_mode CHECK (mode IN ('NAVIGATION', 'IDLE'))
);

CREATE INDEX IF NOT EXISTS idx_visit_positions_visit ON visit_positions (visit_id);
CREATE INDEX IF NOT EXISTS idx_visit_positions_visit_received ON visit_positions (visit_id, received_at);

-- Visit events
CREATE TABLE IF NOT EXISTS visit_events (
    event_id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL REFERENCES visits(visit_id),
    user_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    from_state VARCHAR(20) NOT NULL,
    to_state VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    meta JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_visit_events_visit ON visit_events (visit_id, occurred_at);

-- User place first arrivals
CREATE TABLE IF NOT EXISTS user_place_first_arrivals (
    user_id UUID NOT NULL,
    place_id BIGINT NOT NULL,
    first_arrived_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, place_id),
    CONSTRAINT fk_upfa_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_upfa_place FOREIGN KEY (place_id) REFERENCES places(place_id)
);

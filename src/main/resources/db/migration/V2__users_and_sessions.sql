-- V2: ERD-TBL-001 users, ERD-TBL-002 user_sessions (JWT token registry).

CREATE TABLE users (
    user_id           UUID PRIMARY KEY,
    full_name         VARCHAR(100) NOT NULL,
    email             VARCHAR(255) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    business_role_id  UUID NOT NULL REFERENCES business_roles (business_role_id),
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at    TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_users_business_role_id ON users (business_role_id);

-- ERD-TBL-002: one row per issued JWT, keyed by SHA-256(jti). Raw JWT/jti is never persisted.
CREATE TABLE user_sessions (
    session_id         UUID PRIMARY KEY,
    user_id            UUID NOT NULL REFERENCES users (user_id),
    session_token_hash VARCHAR(64) NOT NULL UNIQUE,
    ip_address         INET,
    user_agent         TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at         TIMESTAMPTZ NOT NULL,
    is_revoked         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_user_sessions_user_id ON user_sessions (user_id);
-- SRS-REQ-079 / ERD-CON-001: fast lookup for the per-request revalidation path.
CREATE INDEX ix_user_sessions_active_lookup ON user_sessions (session_token_hash) WHERE is_revoked = FALSE;

-- Seed the initial CEO account so the system is bootstrappable.
-- Password: "ChangeMe123!" (BCrypt, verified) - MUST be rotated immediately after first login in any non-dev environment.
INSERT INTO users (user_id, full_name, email, password_hash, business_role_id, is_active) VALUES
    ('01926e3e-0002-7000-8000-000000000001', 'KCPC CEO', 'ceo@kcpcbandhani.local',
     '$2y$10$Bl3wW1y97o421M44TQ/wYeS8udWgdrYuQcK6LryXvdx5PUfkHqq2W',
     '01926e3e-0001-7000-8000-000000000001', TRUE);

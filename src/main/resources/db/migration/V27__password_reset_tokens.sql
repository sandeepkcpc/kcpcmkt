-- V27: password_reset_tokens - self-service "Forgot Password" flow. Mirrors user_sessions' own
-- token-registry pattern exactly (ERD-CON-... same spirit as SAD-ADR-001): only SHA-256(raw
-- token) is ever persisted, the raw token itself never touches the database. Single-use
-- (used_at transitions once, NULL -> set; PasswordResetService checks it before honoring a
-- token) and a short (30-minute) expiry window, both enforced at the application layer
-- (PasswordResetService), matching this schema's existing convention of enforcing token-registry
-- semantics in the service layer rather than via DB triggers for a row that legitimately needs
-- one controlled UPDATE (marking used_at) - the same precedent as work_hold_records' resume field.

CREATE TABLE password_reset_tokens (
    reset_token_id  UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users (user_id),
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    requested_ip    INET
);

CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);
-- Fast lookup for the one hot path (validating a presented token) - mirrors user_sessions'
-- analogous partial index over its own active-lookup column.
CREATE INDEX ix_password_reset_tokens_active_lookup ON password_reset_tokens (token_hash) WHERE used_at IS NULL;

-- V13's privilege split (Part B) only granted kcpc_app privileges on tables that existed at that
-- time; this table is new, so it needs its own grant, guarded the same way V13/V15/V17/V25 guard
-- themselves (safely no-op where kcpc_app is the same role as the migrator, e.g. local dev/test).
-- SELECT/INSERT/UPDATE only (matching work_hold_records) - marking a token used is the one
-- legitimate UPDATE; rows are never deleted.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kcpc_app')
       AND to_regrole('kcpc_app') IS DISTINCT FROM to_regrole(current_user) THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE ON password_reset_tokens TO kcpc_app';
    END IF;
END $$;

-- V5: ERD-TBL-025 system_audit_log. System-wide append-only audit trail (SAD-DES-006).
-- DB-privilege append-only enforcement: see docs/IMPLEMENTATION_DECISIONS.md "DB-001" (Phase 14).

CREATE TABLE system_audit_log (
    audit_id                   UUID PRIMARY KEY,
    event_timestamp             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_user_id                UUID NOT NULL REFERENCES users (user_id),
    actor_base_role_code         VARCHAR(30) NOT NULL,
    acting_permission_grant_id   UUID REFERENCES permission_grants (grant_id),
    event_category                VARCHAR(50) NOT NULL,
    event_type                    VARCHAR(50) NOT NULL,
    target_entity_name            VARCHAR(50) NOT NULL,
    target_entity_id              UUID NOT NULL,
    previous_state_snapshot       JSONB,
    new_state_snapshot            JSONB,
    action_reason                 TEXT,
    ip_address                    INET
);

CREATE INDEX ix_system_audit_log_actor ON system_audit_log (actor_user_id);
CREATE INDEX ix_system_audit_log_target ON system_audit_log (target_entity_name, target_entity_id);
CREATE INDEX ix_system_audit_log_event_timestamp ON system_audit_log (event_timestamp);

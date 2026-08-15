-- V8: Planning stage (Stage 3) physical objects.
-- ERD-TBL-017 platforms, ERD-TBL-018 company_channels, ERD-TBL-019 publication_targets,
-- ERD-TBL-011 planned_outputs, ERD-TBL-020 planned_output_publication_target_mappings,
-- ERD-TBL-041 content_plan_talent_entries, ERD-TBL-037 planning_preparers,
-- ERD-TBL-013 shooting_assignments.

CREATE TABLE platforms (
    platform_id      UUID PRIMARY KEY,
    platform_name    VARCHAR(50) NOT NULL UNIQUE,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at   TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- BFD Appendix B.A: 6 governed platform seeds.
INSERT INTO platforms (platform_id, platform_name) VALUES
    ('01926e3e-0008-7000-8000-000000000001', 'Instagram'),
    ('01926e3e-0008-7000-8000-000000000002', 'Threads'),
    ('01926e3e-0008-7000-8000-000000000003', 'YouTube'),
    ('01926e3e-0008-7000-8000-000000000004', 'Facebook'),
    ('01926e3e-0008-7000-8000-000000000005', 'Moj'),
    ('01926e3e-0008-7000-8000-000000000006', 'TikTok');

CREATE TABLE company_channels (
    channel_id       UUID PRIMARY KEY,
    channel_handle   VARCHAR(100) NOT NULL UNIQUE,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at   TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- BFD Appendix B.B: 8 governed company channel/account seeds.
INSERT INTO company_channels (channel_id, channel_handle) VALUES
    ('01926e3e-0009-7000-8000-000000000001', 'piyushxbusiness'),
    ('01926e3e-0009-7000-8000-000000000002', 'kcpcsikar'),
    ('01926e3e-0009-7000-8000-000000000003', 'kcpclegacy'),
    ('01926e3e-0009-7000-8000-000000000004', 'kcpcbandhani.01'),
    ('01926e3e-0009-7000-8000-000000000005', 'kcpcbandhani'),
    ('01926e3e-0009-7000-8000-000000000006', 'kcpc.english'),
    ('01926e3e-0009-7000-8000-000000000007', 'kcpc_sikar'),
    ('01926e3e-0009-7000-8000-000000000008', 'kcpc_legacy');

CREATE TABLE publication_targets (
    publication_target_id UUID PRIMARY KEY,
    platform_id            UUID NOT NULL REFERENCES platforms (platform_id),
    channel_id              UUID NOT NULL REFERENCES company_channels (channel_id),
    target_name              VARCHAR(150) NOT NULL,
    is_active                 BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_publication_targets_platform_channel UNIQUE (platform_id, channel_id) -- ERD-CON-032 (active targets)
);

-- A representative starter set so Planning/Publishing are exercisable before Phase 11's full
-- catalogue admin UI exists; CEO/MM extend this via Permission #17 catalogue management later.
INSERT INTO publication_targets (publication_target_id, platform_id, channel_id, target_name) VALUES
    ('01926e3e-000a-7000-8000-000000000001',
     '01926e3e-0008-7000-8000-000000000001', '01926e3e-0009-7000-8000-000000000005', 'Instagram · kcpcbandhani'),
    ('01926e3e-000a-7000-8000-000000000002',
     '01926e3e-0008-7000-8000-000000000003', '01926e3e-0009-7000-8000-000000000005', 'YouTube · kcpcbandhani'),
    ('01926e3e-000a-7000-8000-000000000003',
     '01926e3e-0008-7000-8000-000000000004', '01926e3e-0009-7000-8000-000000000005', 'Facebook · kcpcbandhani');

CREATE TABLE planned_outputs (
    planned_output_id  UUID PRIMARY KEY,
    content_plan_id    UUID NOT NULL REFERENCES content_plans (content_plan_id),
    output_type        VARCHAR(30) NOT NULL,
    reel_type          VARCHAR(20),
    title_description  VARCHAR(200),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_planned_outputs_type CHECK (output_type IN ('PHOTOGRAPHY', 'REEL', 'VIDEO')), -- ERD-CON-007
    CONSTRAINT ck_planned_outputs_reel_type -- ERD-CON-008 / ERD-CON-054
        CHECK ((output_type = 'REEL' AND reel_type IN ('VERY_SHORT', 'SHORT', 'LONG'))
            OR (output_type <> 'REEL' AND reel_type IS NULL))
);

CREATE INDEX ix_planned_outputs_content_plan ON planned_outputs (content_plan_id);

CREATE TABLE planned_output_publication_target_mappings (
    mapping_id              UUID PRIMARY KEY,
    planned_output_id       UUID NOT NULL REFERENCES planned_outputs (planned_output_id),
    publication_target_id   UUID NOT NULL REFERENCES publication_targets (publication_target_id),
    CONSTRAINT uq_output_target_mapping UNIQUE (planned_output_id, publication_target_id) -- ERD-CON-031
);

CREATE TABLE content_plan_talent_entries (
    entry_id           UUID PRIMARY KEY,
    content_plan_id     UUID NOT NULL REFERENCES content_plans (content_plan_id),
    talent_name         VARCHAR(100) NOT NULL
);

CREATE INDEX ix_content_plan_talent_entries_plan ON content_plan_talent_entries (content_plan_id);

-- ERD-TBL-037: Planning preparer provenance for the self-approval-conflict guard.
CREATE TABLE planning_preparers (
    preparer_id         UUID PRIMARY KEY,
    content_plan_id      UUID NOT NULL REFERENCES content_plans (content_plan_id),
    preparer_user_id     UUID NOT NULL REFERENCES users (user_id),
    recorded_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_planning_preparers_plan ON planning_preparers (content_plan_id);

CREATE TABLE shooting_assignments (
    assignment_id            UUID PRIMARY KEY,
    content_plan_id           UUID NOT NULL REFERENCES content_plans (content_plan_id),
    cameraperson_user_id      UUID NOT NULL REFERENCES users (user_id),
    assigned_by_user_id       UUID NOT NULL REFERENCES users (user_id),
    assigned_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active                  BOOLEAN NOT NULL DEFAULT TRUE,
    ended_at                    TIMESTAMPTZ
);

CREATE INDEX ix_shooting_assignments_plan ON shooting_assignments (content_plan_id);
CREATE INDEX ix_shooting_assignments_active ON shooting_assignments (content_plan_id) WHERE is_active = TRUE;

-- DEMO DATA ONLY. Never applied outside the dev/demo Flyway location list (see application.yml
-- "dev" profile spring.flyway.locations) - the production/docker profile never includes this
-- folder. All accounts share password "Demo@123" (BCrypt, verified) for demo convenience.

INSERT INTO users (user_id, full_name, email, password_hash, business_role_id, is_active) VALUES
    ('01926e3e-0002-7000-8000-000000000002', 'Meera Shah', 'mm@kcpcbandhani.local',
     '$2y$10$HMYc896KmUA80jdyWoT/BOs9FbrjoWzu6n6jgbpM7HWaUyB3dZ98a',
     '01926e3e-0001-7000-8000-000000000002', TRUE), -- Marketing Manager
    ('01926e3e-0002-7000-8000-000000000003', 'Rohan Kapoor', 'camera@kcpcbandhani.local',
     '$2y$10$2za8DS2I1zRqtMXRfADMbug7uP.Q37TN3H7Bc52Cn/XnjOqq4hMa6',
     '01926e3e-0001-7000-8000-000000000004', TRUE), -- Camera Person
    ('01926e3e-0002-7000-8000-000000000004', 'Ananya Verma', 'editor@kcpcbandhani.local',
     '$2y$10$8Mocc1Llz0RV3o/NP177YOyG5TitGt/fnh/aWzRVDjHCnPcuAd9kO',
     '01926e3e-0001-7000-8000-000000000005', TRUE), -- Video Editor
    ('01926e3e-0002-7000-8000-000000000005', 'Karan Mehta', 'publisher@kcpcbandhani.local',
     '$2y$10$jePD9lO8nCdiFAmAFnLm9OclVgDiNYeMi8Ur4uHG7XWEjguFjaig.',
     '01926e3e-0001-7000-8000-000000000008', TRUE), -- Publisher
    ('01926e3e-0002-7000-8000-000000000006', 'Vikram Rao', 'camera2@kcpcbandhani.local',
     '$2y$10$6l5hkOJHzs5RPuot/zXZ7OaeQWeXZKmHNH42mVDZLHwaXKZD8VDVm',
     '01926e3e-0001-7000-8000-000000000004', TRUE), -- Camera Person #2 (multi-contributor scenarios)
    ('01926e3e-0002-7000-8000-000000000007', 'Priya Nair', 'editor2@kcpcbandhani.local',
     '$2y$10$0aw7NhiXxB0tcZiy5c051uyNsV0ReoFlNUyCj4E7pMWJQsWhEHqT2',
     '01926e3e-0001-7000-8000-000000000005', TRUE), -- Video Editor #2 (multi-contributor scenarios)
    ('01926e3e-0002-7000-8000-000000000008', 'Sanya Kapoor', 'coordinator@kcpcbandhani.local',
     '$2y$10$fnarwufAY.QrTTL1eM2fU.hDdgZkHU4/gIPL6MJW0Lb7oK/uud9K2',
     '01926e3e-0001-7000-8000-000000000006', TRUE), -- Marketing Coordinator (delegated reviewer, below)
    ('01926e3e-0002-7000-8000-000000000009', 'Devika Joshi', 'hr@kcpcbandhani.local',
     '$2y$10$uTjc0kNfhEXkq8DP93l3k.dsoVzBI3ReGIripyGDgeHlRHW7Ab3Ge',
     '01926e3e-0001-7000-8000-000000000003', TRUE); -- HR Manager (plain employee, no delegated permission)

-- Grant the Publisher demo user Permission #8 (Publishing Execution), globally scoped, so the
-- delegated-permission path is demonstrable end to end alongside the native CEO/MM path.
INSERT INTO permission_grants (grant_id, grantee_user_id, grantor_user_id, permission_number, scope_type, effective_from, is_active) VALUES
    ('01926e3e-0006-7000-8000-000000000001',
     '01926e3e-0002-7000-8000-000000000005',
     '01926e3e-0002-7000-8000-000000000001',
     8, 'GLOBAL', CURRENT_TIMESTAMP, TRUE);

-- Grant the Marketing Coordinator demo user delegated Permission #1 (Idea Review) and
-- Permission #2 (Planning Execution), globally scoped, so the Employee-delegated-reviewer path
-- and the self-review-conflict guard (BRS-REQ-012 / ERD-CON-011) are both demonstrable.
INSERT INTO permission_grants (grant_id, grantee_user_id, grantor_user_id, permission_number, scope_type, effective_from, is_active) VALUES
    ('01926e3e-0006-7000-8000-000000000002',
     '01926e3e-0002-7000-8000-000000000008',
     '01926e3e-0002-7000-8000-000000000001',
     1, 'GLOBAL', CURRENT_TIMESTAMP, TRUE),
    ('01926e3e-0006-7000-8000-000000000003',
     '01926e3e-0002-7000-8000-000000000008',
     '01926e3e-0002-7000-8000-000000000001',
     2, 'GLOBAL', CURRENT_TIMESTAMP, TRUE);

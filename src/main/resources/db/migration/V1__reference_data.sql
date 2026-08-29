-- V1: Fixed reference/lookup catalogues.
-- ERD-TBL-003 base_roles, ERD-TBL-044 business_roles, ERD-TBL-004 operational_permissions,
-- ERD-TBL-036 permission_grant_stages, ERD-TBL-006 workflow_concepts.
-- All primary keys are application-supplied (UUIDv7 client-generated); no DB-side UUID default.

CREATE TABLE base_roles (
    role_code   VARCHAR(30) PRIMARY KEY,
    role_name   VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    CONSTRAINT ck_base_roles_role_code CHECK (role_code IN ('CEO_OWNER', 'MARKETING_MANAGER', 'EMPLOYEE')) -- ERD-CON-001
);

INSERT INTO base_roles (role_code, role_name, description) VALUES
    ('CEO_OWNER', 'CEO / Owner', 'Strategic oversight, idea submission, full operational execution authority, and exclusive user-access administration.'),
    ('MARKETING_MANAGER', 'Marketing Manager', 'Operational owner with full default authority across workflow execution; zero access-administration authority.'),
    ('EMPLOYEE', 'Employee', 'Content creator/executor with default self-service access; additional capability only via CEO-granted Operational Permissions.');

CREATE TABLE business_roles (
    business_role_id  UUID PRIMARY KEY,
    role_name         VARCHAR(100) NOT NULL UNIQUE,
    access_class_code VARCHAR(30) NOT NULL REFERENCES base_roles (role_code), -- ERD-CON-063
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at    TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 17 seeded Business Roles (ERD-TBL-044 seed data).
INSERT INTO business_roles (business_role_id, role_name, access_class_code) VALUES
    ('01926e3e-0001-7000-8000-000000000001', 'CEO', 'CEO_OWNER'),
    ('01926e3e-0001-7000-8000-000000000002', 'Marketing Manager', 'MARKETING_MANAGER'),
    ('01926e3e-0001-7000-8000-000000000003', 'HR Manager', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-000000000004', 'Camera Person', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-000000000005', 'Video Editor', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-000000000006', 'Marketing Coordinator', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-000000000007', 'CEO''s Executive Assistant', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-000000000008', 'Publisher', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-000000000009', 'Model', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-00000000000a', 'Senior Manager', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-00000000000b', 'SEO Executive', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-00000000000c', 'SEO Intern', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-00000000000d', 'Marketing Intern', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-00000000000e', 'Sales Manager', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-00000000000f', 'CRM Manager', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-000000000010', 'Customer Support Executive', 'EMPLOYEE'),
    ('01926e3e-0001-7000-8000-000000000011', 'Marketing Data Operator', 'EMPLOYEE');

CREATE TABLE operational_permissions (
    permission_number INTEGER PRIMARY KEY,
    permission_code   VARCHAR(50) NOT NULL UNIQUE,
    permission_name   VARCHAR(100) NOT NULL,
    description       TEXT NOT NULL,
    CONSTRAINT ck_operational_permissions_number CHECK (permission_number BETWEEN 1 AND 17) -- ERD-CON-002
);

INSERT INTO operational_permissions (permission_number, permission_code, permission_name, description) VALUES
    (1, 'PERM_01_IDEA_REVIEW', 'Idea Review', 'Evaluate submitted ideas: Approve (with predefined Marks), Reject, Retain.'),
    (2, 'PERM_02_PLANNING_EXECUTION', 'Planning Execution', 'Manage an approved Content ID''s planning parameters (Outputs, Reel Types, Publication Scope).'),
    (4, 'PERM_04_SHOOT_ASSIGNMENT', 'Shooting Assignment Management', 'Assign initial shooting team/Cameraperson(s).'),
    (5, 'PERM_05_SHOOT_REVIEW', 'Shoot Review', 'Approve or Request Rework on shoot output; attribute Cameraperson Marks.'),
    (6, 'PERM_06_EDIT_ASSIGNMENT', 'Editing Assignment Management', 'Assign Editor(s) after Shoot Approval.'),
    (7, 'PERM_07_EDIT_REVIEW', 'Edit Review', 'Approve or Request Rework on edited output; attribute Editor Marks.'),
    (8, 'PERM_08_PUBLISHING_EXECUTION', 'Publishing Execution', 'Record Actual Publication events and manage Target N/A.'),
    (9, 'PERM_09_PERFORMANCE_UPDATE', 'Performance Update', 'Enter/submit Creative Performance Scorecard metrics.'),
    (10, 'PERM_10_RESCHEDULE', 'Reschedule', 'Modify approved production dates with mandatory reason.'),
    (11, 'PERM_11_REASSIGN', 'Reassign', 'Replace existing shooting/editing assignees with mandatory reason.'),
    (12, 'PERM_12_CANCEL', 'Cancel', 'Cancel a pre-completion deliverable with mandatory reason.'),
    (13, 'PERM_13_FOLDER_LINK_MANAGE', 'Content Asset Folder Link Management', 'Create/replace the parent asset folder link.'),
    (14, 'PERM_14_TEAM_WORKLOAD_VIEW', 'Team Workload Visibility', 'View contextual team workload summaries.'),
    (15, 'PERM_15_TEAM_KPI_VIEW', 'Team KPI Visibility', 'View team-level KPI dashboards.'),
    (16, 'PERM_16_AUDIT_HISTORY_VIEW', 'Relevant Audit-History Visibility', 'View permitted audit-history records.'),
    (17, 'PERM_17_PLATFORM_CATALOGUE_MANAGE', 'Platform & Channel Catalogue Management', 'Administer Platform/Company Channel/Publication Target master data.');

CREATE TABLE permission_grant_stages (
    stage_number INTEGER PRIMARY KEY,
    stage_code   VARCHAR(30) NOT NULL UNIQUE,
    stage_name   VARCHAR(100) NOT NULL
);

INSERT INTO permission_grant_stages (stage_number, stage_code, stage_name) VALUES
    (1, 'IDEA_MANAGEMENT', 'Idea Submission & Review'),
    (2, 'PLANNING', 'Planning'),
    (3, 'SHOOTING', 'Shooting Execution & Review'),
    (4, 'EDITING', 'Editing Execution & Review'),
    (5, 'PUBLISHING', 'Publishing'),
    (6, 'PERFORMANCE', 'Performance Tracking & Completion'),
    (7, 'ADMINISTRATIVE', 'Cross-Cutting Administrative Actions');

CREATE TABLE workflow_concepts (
    status_code       VARCHAR(10) PRIMARY KEY,
    concept_number    INTEGER NOT NULL UNIQUE,
    status_name       VARCHAR(50) NOT NULL,
    classification    VARCHAR(30) NOT NULL,
    is_primary_status BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_workflow_concepts_classification
        CHECK (classification IN ('ACTIVE', 'DORMANT', 'TERMINAL', 'CLOSED', 'SUPPLEMENTARY_FLAG'))
);

INSERT INTO workflow_concepts (status_code, concept_number, status_name, classification, is_primary_status) VALUES
    ('IS',   1,  'Idea Submitted',       'ACTIVE',              TRUE),
    ('PA',   2,  'Pending Approval',     'ACTIVE',              TRUE),
    ('RJ',   3,  'Rejected',             'TERMINAL',            TRUE),
    ('RET',  4,  'Retained',             'DORMANT',             TRUE),
    ('SA',   8,  'Shoot Assigned',       'ACTIVE',              TRUE),
    ('SIP',  9,  'Shoot In Progress',    'ACTIVE',              TRUE),
    ('SRV',  10, 'Shoot Review',         'ACTIVE',              TRUE),
    ('SAP',  11, 'Shoot Approved',       'ACTIVE',              TRUE),
    ('EA',   12, 'Edit Assigned',        'ACTIVE',              TRUE),
    ('ED',   13, 'Editing',              'ACTIVE',              TRUE),
    ('ERV',  14, 'Edit Review',          'ACTIVE',              TRUE),
    ('EAP',  15, 'Edit Approved',        'ACTIVE',              TRUE),
    ('RFP',  16, 'Ready for Publishing', 'ACTIVE',              TRUE),
    ('PUBG', 17, 'Publishing',           'ACTIVE',              TRUE),
    ('PP',   18, 'Performance Pending',  'ACTIVE',              TRUE),
    ('PFUP', 19, 'Performance Update',   'ACTIVE',              TRUE),
    ('COMP', 20, 'Completed',            'CLOSED',              TRUE),
    ('DLY',  21, 'Delayed',              'SUPPLEMENTARY_FLAG',  FALSE),
    ('CAN',  22, 'Cancelled',            'TERMINAL',            TRUE);

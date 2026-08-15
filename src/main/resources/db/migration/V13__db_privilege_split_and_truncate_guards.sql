-- V13: DB-001 closure.
-- Part A: fills the two remaining ERD-CON-058/062 DB-trigger gaps V12 left open:
--   (1) work_hold_records hard DELETE was never blocked (ERD-CON-062 rule 9) - V9 only guarded
--       UPDATE (rules 6-8); V12 deliberately excluded this table from its blanket UPDATE/DELETE
--       trigger since its UPDATE semantics are not simple reject-everything.
--   (2) TRUNCATE was never blocked on any append-only table. Postgres row-level BEFORE triggers
--       (used in V9/V12 for UPDATE/DELETE) do not fire on TRUNCATE at all - it needs its own
--       FOR EACH STATEMENT trigger.
-- Part B: the actual DB-privilege-layer split (ERD-CON-018/035/047/058: "database roles MUST
-- reject UPDATE/DELETE/TRUNCATE"), belt-and-suspenders alongside the trigger guards above. This
-- migration must run under the schema-owning role (kcpc_migrator in docker/prod - see
-- db/init/01_create_app_role.sh); it grants the restricted runtime role (kcpc_app) exactly the
-- privileges the application needs and nothing more. In any environment where kcpc_app has not
-- been created as a separate restricted role (e.g. today's single-role local dev setup), this
-- block is a documented no-op guarded by an existence check.

-- --- Part A ----------------------------------------------------------------

CREATE OR REPLACE FUNCTION trg_work_hold_records_reject_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'work_hold_records: hard DELETE is prohibited (ERD-CON-062 rule 9)';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_work_hold_records_reject_delete
    BEFORE DELETE ON work_hold_records
    FOR EACH ROW EXECUTE FUNCTION trg_work_hold_records_reject_delete();

CREATE OR REPLACE FUNCTION trg_reject_truncate() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; TRUNCATE is not permitted (ERD-CON-058)', TG_TABLE_NAME;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'predefined_mark_corrections', 'publication_evidence_corrections', 'performance_metric_corrections',
        'workflow_transition_history', 'personal_mark_attributions', 'actual_publication_events',
        'publication_target_na_records', 'reschedule_records', 'reassignment_records',
        'reassignment_assignees', 'cancellation_records', 'reopen_records', 'planning_preparers',
        'shooting_execution_participants', 'editing_execution_participants', 'system_audit_log',
        'work_hold_records'
    ]
    LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%1$s_reject_truncate BEFORE TRUNCATE ON %1$s FOR EACH STATEMENT EXECUTE FUNCTION trg_reject_truncate();',
            t);
    END LOOP;
END $$;

-- --- Part B ----------------------------------------------------------------

DO $$
DECLARE
    rec RECORD;
    append_only_tables TEXT[] := ARRAY[
        'predefined_mark_corrections', 'publication_evidence_corrections', 'performance_metric_corrections',
        'workflow_transition_history', 'personal_mark_attributions', 'actual_publication_events',
        'publication_target_na_records', 'reschedule_records', 'reassignment_records',
        'reassignment_assignees', 'cancellation_records', 'reopen_records', 'planning_preparers',
        'shooting_execution_participants', 'editing_execution_participants', 'system_audit_log'
    ];
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kcpc_app')
       AND to_regrole('kcpc_app') IS DISTINCT FROM to_regrole(current_user) THEN

        EXECUTE 'GRANT USAGE ON SCHEMA public TO kcpc_app';

        EXECUTE format('GRANT SELECT, INSERT ON %s TO kcpc_app',
            array_to_string(ARRAY(SELECT quote_ident(x) FROM unnest(append_only_tables) AS x), ', '));

        -- work_hold_records: SELECT/INSERT/UPDATE (the one controlled Resume UPDATE; V9's
        -- trg_work_hold_records_lifecycle row-level trigger still enforces which UPDATE is
        -- legal) but no DELETE - covered separately by Part A above.
        EXECUTE 'GRANT SELECT, INSERT, UPDATE ON work_hold_records TO kcpc_app';

        FOR rec IN
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
              AND table_name <> ALL (append_only_tables)
              AND table_name NOT IN ('work_hold_records', 'flyway_schema_history')
        LOOP
            EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON %I TO kcpc_app', rec.table_name);
        END LOOP;

        FOR rec IN SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public'
        LOOP
            EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %I TO kcpc_app', rec.sequence_name);
        END LOOP;
    END IF;
END $$;

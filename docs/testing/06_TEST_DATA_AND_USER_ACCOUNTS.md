# 06 — Test Data and User Accounts

## 1. Starting the application for UAT

```bash
cd <repository root>
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The application starts on **`http://localhost:8080`**. Flyway applies all database migrations automatically on startup — no manual schema setup needed. This requires a local PostgreSQL 16+ instance and a `kcpc_dev` database to already exist (one-time setup, see `README.md` — a developer/technical setup step, not part of day-to-day UAT).

**Confirm it started cleanly:** the console should show `Started KcpcMktApplication` with no `ERROR` lines above it.

## 2. Test / demo accounts (dev profile only)

These accounts are seeded automatically by `db/migration-demo/V6__demo_users.sql`, which **only loads under the `dev` profile** — it is never present in the `docker`/production configuration. This means UAT with these accounts must run against the `dev` profile as shown above.

| Name | Login Email | Business Role | Access Class | Delegated Permissions | Purpose in UAT |
|---|---|---|---|---|---|
| KCPC CEO | `ceo@kcpcbandhani.local` | CEO | CEO_OWNER | none (native authority) | Primary UAT actor — can do everything natively; also used to administer other accounts |
| Meera Shah | `mm@kcpcbandhani.local` | Marketing Manager | MARKETING_MANAGER | none (native authority) | Second native-authority actor — proves CEO isn't the only role with full operational authority |
| Rohan Kapoor | `camera@kcpcbandhani.local` | Camera Person | EMPLOYEE | none | Plain Employee with zero delegated grants — negative-path/permission-denial testing; also the primary Cameraperson for the Golden Flow |
| Vikram Rao | `camera2@kcpcbandhani.local` | Camera Person | EMPLOYEE | none | Second Cameraperson — multi-contributor Mark scenarios |
| Ananya Verma | `editor@kcpcbandhani.local` | Video Editor | EMPLOYEE | none | Plain Employee with zero delegated grants; primary Editor for the Golden Flow |
| Priya Nair | `editor2@kcpcbandhani.local` | Video Editor | EMPLOYEE | none | Second Editor — multi-contributor Mark scenarios |
| Karan Mehta | `publisher@kcpcbandhani.local` | Publisher | EMPLOYEE | Permission #8 (Publishing Execution), GLOBAL scope | Proves the delegated-permission path works for a real business action, alongside the native CEO/MM path |
| Sanya Kapoor | `coordinator@kcpcbandhani.local` | Marketing Coordinator | EMPLOYEE | Permission #1 (Idea Review) + Permission #2 (Planning Execution), GLOBAL scope | The one Employee account that can approve/reject Ideas and execute Planning — used to prove the delegated-reviewer path AND the self-review-conflict guard (this account must be blocked from deciding on an idea it submitted itself) |
| Devika Joshi | `hr@kcpcbandhani.local` | HR Manager | EMPLOYEE | none | Plain Employee — general negative-path testing |

**Passwords:** the CEO account's password is `ChangeMe123!` (seeded in every profile, including production — rotate it immediately outside of local/dev use). Every other account above shares the password `Demo@123` (dev-profile convenience only — see `V6__demo_users.sql`). **Do not put these passwords into any shared/external document** — they are reproduced here only because this file itself is a local, dev-profile-only testing artifact; treat it the same way you'd treat the seed script itself.

**No missing personas:** every role/permission scenario called for elsewhere in this UAT package (native CEO, native MM, plain Employee, delegated reviewer with self-review-conflict exposure, delegated Publishing actor, multi-Cameraperson, multi-Editor) has a real, already-seeded account above. No **TEST PERSONA MISSING** situations were found.

## 3. Creating additional test users during UAT

If a scenario needs a fresh user (e.g. to test an expired/revoked/out-of-scope grant without disturbing the seeded accounts), use the CEO account:

1. Log in as CEO.
2. Go to **Users** (`/app/admin/users`) → fill the Create User form (Full Name, Email, Initial Password, Business Role, Reason) → **Create User**.
3. To grant a delegated permission: open the new user (`/app/admin/users/{id}`) → **Grant Permission** panel.

This is a real, governed, CEO-exclusive action (Permission administration) — not a database shortcut.

## 4. Database reset behaviour

- **Restarting the application does NOT reset data.** Flyway migrations run once each (tracked in the `flyway_schema_history` table); on every subsequent restart, Flyway sees the schema is already up to date and does nothing. Any data created during a UAT session (new ideas, users, grants, etc.) persists across restarts.
- **To get a genuinely fresh/empty database** (e.g. before a formal sign-off run): drop and recreate the `kcpc_dev` database, then start the application again — Flyway will re-run every migration from scratch, including the demo-user seed data.
  ```bash
  dropdb kcpc_dev && createdb -O kcpc_app kcpc_dev
  mvn spring-boot:run -Dspring-boot.run.profiles=dev
  ```
  This is a destructive, technical action — coordinate with whoever is running parallel tests before doing this, and note the reset in the Defect Log / Final UAT Report so results are attributable to a known starting point.
- **There is no "soft reset" or built-in test-data-wipe feature in the application itself** — this is a genuine implementation gap for a fast repeat-testing workflow, not a UAT process choice. If daily UAT sign-off needs a clean slate, plan for the drop/recreate above.

## 5. Avoiding corrupting historical test evidence

The system is deliberately **append-only** for governed history — corrections never overwrite original records (Marks, publication evidence, performance metrics all use linked correction ledgers instead of edits). This means:

- You cannot accidentally destroy prior UAT evidence by "fixing" a mistake through the UI — every correction action creates a new linked row and preserves the original.
- The one way to lose UAT history is the database drop/recreate in Section 4 above. Do that only when you deliberately want a clean slate, and record when you did it.
- Screenshots (see `01_TESTING_OVERVIEW_AND_STRATEGY.md` and the Golden Flow doc) are still your best evidence trail across a reset, since the database itself doesn't survive a drop/recreate.

## 6. Resetting a disposable test-user password

There is no "forgot password" self-service flow in this MVP (not part of the governed R3.5 scope). To reset a test user's password:

1. Log in as CEO.
2. Deactivate the account is **not** the way to reset a password — deactivation only blocks login, it doesn't touch credentials.
3. The only governed password-change path visible in the UI is account creation itself (an initial password is set at creation time). If a disposable test account's password is lost, the practical path during UAT is to create a new disposable test user via Section 3 above rather than attempt an undocumented reset.

## 7. Publication targets, platforms, and channels used in this UAT package

These are real, pre-seeded reference data (not invented):

| Target Name (as shown in the UI) | Platform | Channel |
|---|---|---|
| Instagram · kcpcbandhani | Instagram | `kcpc_sikar` account family |

Six platforms (Instagram, Threads, YouTube, Moj, TikTok, +1 more) and eight company channels are seeded; the Golden Flow and functional test cases in this package use only the one target above for simplicity. Testers are free to exercise additional platform/channel/target combinations — see `03_COMPLETE_FUNCTIONAL_UAT_TEST_CASES.md` (UAT-PUB tests) and the Publishing Catalogue screen (`/app/admin/catalogue`, CEO/MM or Permission #17).

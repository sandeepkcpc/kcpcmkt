# User CSV Import Guide

`Administration → Users → Import Users` lets a CEO bulk-create user accounts from a CSV file,
instead of using the one-at-a-time `Create User` form for every employee.

Template: [`import-templates/KCPC_USER_IMPORT_TEMPLATE.csv`](import-templates/KCPC_USER_IMPORT_TEMPLATE.csv)

## Who can use this

CEO (Access Class `CEO_OWNER`) only — the same authority required for the existing `Create User`
form and every other User Administration action.

## Columns

| Column | Required | Allowed values | Notes |
|---|---|---|---|
| `full_name` | Yes | any non-blank text | Trimmed. |
| `email` | Yes | a valid email address | Trimmed, matched case-insensitively for uniqueness. Must be unique within the file and unique against existing users. |
| `business_role` | Yes | an exact, active Business Role name (e.g. `Camera Person`, `Video Editor`, `Marketing Manager`, `CEO`) | Matched case-insensitively but **not** fuzzy-matched — `Vido Editor` is rejected, it will not silently match `Video Editor`. See your Business Role catalogue at `Administration → Business Roles` for the exact current list. |
| `account_status` | No | `ACTIVE` or `INACTIVE` (case-insensitive) | Blank/omitted defaults to `ACTIVE`. |

There is **no `employee_code` column** and **no `access_class` column**:

- The current schema has no employee-code field on the User record at all, so nothing would be
  stored even if one were supplied.
- Access Class (`CEO_OWNER` / `MARKETING_MANAGER` / `EMPLOYEE`) is never stored independently — it
  is always derived from whichever Business Role the user holds (`CEO → CEO_OWNER`,
  `Marketing Manager → MARKETING_MANAGER`, everyone else → `EMPLOYEE`). It is shown as a read-only,
  informational column on the Preview screen so you can visually confirm each row resolved to the
  Business Role you intended, but it is never something you can set directly from the CSV.

No column in this import ever grants an operational permission (Shoot/Edit/Publishing Execution,
Review, Assignment-management, Admin, etc.), and no column imports workflow assignments,
contributions, KPI history, or content records. Those all remain managed exactly as they are today,
through `Administration → Permissions` and the normal workflow screens.

## Passwords

This application has no password-reset flow and no email-sending capability, so CSV import cannot
send credentials to anyone. Instead, each newly created user is given a random, secure password
generated on the server. Every generated password is shown to the CEO **exactly once**, on the
Import Result screen — copy it and hand it to the employee out-of-band (in person, over a call,
etc.). It is never written to the CSV, never logged, and never stored anywhere in plain text — the
database only ever stores its bcrypt hash, the same as any other user's password.

## How to import

1. Go to `Administration → Users` and click **Import Users**.
2. Choose your CSV file and click **Validate & Preview**. No data is written to the database at
   this step.
3. Review the Preview screen:
   - **Summary**: total rows, valid rows, invalid rows, in-file duplicates, and rows matching an
     existing account.
   - **Row Errors**: one line per problem, in the form `Row 7 — Email is missing`.
   - **Row Detail**: every row with its resolved Business Role, resolved (read-only) Access Class,
     resolved Account Status, and what will happen to it.
4. Click **Import N User(s)** to confirm. The file is re-validated at this moment (in case anything
   changed since you opened the preview, such as another admin creating a conflicting email in the
   meantime) and then imported.
5. The Result screen shows: Total Rows / Imported / Skipped Existing / Failed / Rejected, the
   generated password for every newly created user, and the reason for every skipped/failed/
   rejected row.

## Validation rules

- Required fields (`full_name`, `email`, `business_role`) must be present and non-blank.
- `email` must be a syntactically valid email address.
- `email` must not repeat within the same CSV file (first valid occurrence is kept; later
  occurrences are rejected as `Duplicate email within this file`).
- `email` must not already belong to an existing user (`A user with this email already exists`).
- `business_role` must exactly match (case-insensitive) an active Business Role name.
- `account_status`, if present, must be `ACTIVE` or `INACTIVE`.
- Blank rows are skipped silently (not counted as errors) — useful for trailing blank lines some
  spreadsheet tools add on export.
- Missing required headers or an unparseable file are reported as file-level errors before any row
  is evaluated, and the row table is not shown.
- All values are trimmed of leading/trailing whitespace before validation.

## Duplicate handling

- **New, valid row** → imported as a new user.
- **Row whose email matches an existing user** → skipped, reported as "Existing — will skip". The
  existing user is never modified or overwritten by this import.
- **Invalid row** (missing/malformed field, unknown Business Role, invalid status, duplicate within
  the file) → rejected; no user is created for that row.
- There is no update/upsert behavior — this import only ever creates brand-new users.
- Re-running the same import a second time is always safe: every row that was already imported the
  first time now matches an existing email, so it is skipped rather than duplicated.

## Import safety

Each row is imported independently. One row failing (e.g. a race against another admin) never
rolls back or blocks any other row — the Result screen always gives an unambiguous per-row outcome
(`Imported` / `Skipped Existing` / `Failed` / `Rejected`), matching a summary shape like:

```
Total Rows: 84
Imported: 76
Skipped Existing: 5
Failed: 3
```

## Auditability

Every imported user produces the same `USER_CREATED` audit record that manual `Create User` already
produces, with the reason recorded as "Imported via CSV: <filename> (row N)". In addition, one
batch-summary audit event (`USER_CSV_IMPORT_COMPLETED`) is recorded per import run, capturing the
filename and the total/imported/skipped/failed counts. This reuses the existing audit/logging model
— no separate, second audit system was introduced.

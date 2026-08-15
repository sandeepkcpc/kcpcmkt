# 09 — Defect Log

## Severity scale (plain-language)

| Severity | Meaning | Example |
|---|---|---|
| **BLOCKER** | Nothing downstream can be tested until this is fixed | Login doesn't work at all |
| **CRITICAL** | A core governed business rule is broken or a security boundary fails | An Employee without permission can approve an Idea anyway |
| **MAJOR** | A real feature is broken or a governed rule is violated, but there's a workaround or it's not on the critical path | Reschedule doesn't preserve the old date historically |
| **MINOR** | A real but low-impact defect | A field label is technically wrong but not confusing |
| **COSMETIC** | Visual/styling only, no functional impact | Button alignment |

**Reminder:** basic/plain styling is never itself a defect in this MVP. Only classify something as a defect if it's genuinely functional (wrong behavior, wrong data, missing validation, a security gap, a missing governed screen) — see `01_TESTING_OVERVIEW_AND_STRATEGY.md` §6 and §7 for the exact line between "functional" and "visual improvement."

## Register format

| Defect ID | Date | Test Case | Module | User | Requirement | Expected Result | Actual Result | Severity | Screenshot/Evidence | Assigned To | Fix Status | Retest Result | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| DEF-001 | | | | | | | | | | | | | |

Copy the row above for each new defect found. Use `DEF-001`, `DEF-002`, ... sequentially — never reuse a number, even if a defect turns out to be invalid (mark it "Not a defect" in Fix Status instead of deleting the row, so the investigation trail is preserved).

## Known, already-logged items (carried over from engineering — not new UAT findings)

These are genuine, currently-open implementation deviations already tracked in `docs/IMPLEMENTATION_DISCREPANCIES.md`. They are reproduced here so UAT testers don't waste time re-discovering and re-logging them as if new — but they should still be exercised in UAT to confirm the described behavior, and any *new* symptom beyond what's described here should get its own fresh Defect ID.

| Defect ID | Test Case | Module | Requirement | Expected Result | Actual Result | Severity | Status |
|---|---|---|---|---|---|---|---|
| DEF-KNOWN-001 | UAT-PERM (Permission #13) | Permission Administration | `API-OP-019`, `PERM_13_FOLDER_LINK_MANAGE` | Folder Link changes gated on a dedicated Permission #13 | Gated on Permission #2 (Planning Execution) instead; Permission #13 is never independently checked anywhere | Major | Open — see `DISC-002`, proposed fix is a dedicated follow-up endpoint |
| DEF-KNOWN-002 | UAT-ADMIN (Hold/Resume/Reschedule/Reassign/Cancel/Reopen paths) | API routing | `API_Specification.md` §17.8, Domain 8 | Endpoints at `/api/v1/workflows/{workflowInstanceId}/...` per the literal spec text | Implemented at `/api/v1/content-plans/{id}/...` instead (consistent with every other domain in the app) | Minor (cosmetic path-convention only — functionally correct) | Open — see `DISC-001`, recommend amending the spec to document the as-built convention |
| DEF-KNOWN-003 | UAT-PRIV-010 (Delayed Deliverables scope) | Reporting | `SRS-REQ-002` | A `STAGE_RESTRICTED`/`ITEM_SPECIFIC`-scoped Employee sees every delayed item their grant's exact scope covers | They currently see only their own assigned/participated items — a conservative under-exposure, never a privacy leak, but not the fully faithful scope evaluation the spec describes | Minor | Open — see `ENG-028` in `docs/IMPLEMENTATION_DECISIONS.md` |

## Guidance on filling in the register during a UAT session

- **Requirement** — cite the governed ID if known (`BRS-REQ-xxx`, `SRS-REQ-xxx`, `API-OP-xxx`, `ERD-CON-xxx`) from `10_REQUIREMENT_TEST_TRACEABILITY.md`; if unknown, write "TBD — see traceability doc" and fill it in later.
- **Expected Result** — always phrase this as what the *frozen specification* says, not what you personally expected. If you don't know what the spec says, write "unclear — see below" and flag it as a possible specification gap rather than guessing.
- **Actual Result** — exactly what happened, including any error message text verbatim.
- **Screenshot/Evidence** — file name, following the `UAT-<TEST-ID>-<short-description>.png` convention used in doc 02.
- **Retest Result** — after a fix is applied, re-run the exact same steps and record Pass/Fail again here; do not overwrite the original Actual Result.

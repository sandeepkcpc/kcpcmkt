# Development Handoff — KCPC Marketing Content Production Lifecycle MVP

**Document ID:** `KCPC-MKT-R3.5-HANDOFF-001`
**Date:** August 14, 2026
**Classification:** Confidential — Internal Use Only

---

## Develop against

**Development Baseline R3.5 — FROZEN FOR IMPLEMENTATION** (`BASELINE_FREEZE_R3.5.md`)

## Stack

Java · Spring Boot · Spring Security · JWT · Hibernate/JPA · PostgreSQL · Spring MVC · JSP · HTML/CSS · REST `/api/v1` · Swagger/OpenAPI · Docker-compatible Linux deployment (Ubuntu VPS behind Nginx)

Not used: Node.js runtime · React · MySQL · microservices.

## Architecture rule

```
Browser
  ↓
Nginx
  ↓
Spring Boot application  (modular monolith)
  ├─ Spring MVC + JSP          ─┐
  ├─ REST /api/v1              ─┼─ both call the SAME application/service layer
  ├─ Spring Security + JWT      │
  └─ shared application/service layer
          ↓
     Hibernate / JPA
          ↓
     PostgreSQL 16+
```

**MVC controllers and REST controllers use the same application/service layer.** Business logic lives in that layer — never in a JSP, never duplicated between the two controller families.

## Security rules

- JWT delivered in a **Secure, HttpOnly, SameSite=Lax cookie**, `Path=/`
- **No `localStorage`** for the primary authentication token
- **Server-side token registry** — the JWT is deliberately stateful; `SHA-256(jti)` is stored in `user_sessions` (`ERD-TBL-002`)
- **Logout revokes** (`is_revoked = TRUE`); it does not delete registry rows
- **Account deactivation revokes** the user's active tokens, effective immediately
- **Every authenticated request revalidates** token and account state server-side. The per-request check is required, not an optimization to remove — a purely stateless JWT would silently break logout and deactivation behaviour governed by `AC-003.1`/`AC-003.2` and `SRS-REQ-003`/`SRS-REQ-005`
- **CSRF synchronizer token on every unsafe browser-cookie-authenticated request**
- Access-class and Operational Permission checks are **server-authoritative**. JSP renders a permission-filtered UI; the server still enforces. **The frontend is never the security authority.**

## Authoritative build documents

`Business_Foundation_Document.md` · `Business_Requirements_Specification.md` · `Software_Requirements_Specification.md` · `Requirements_Traceability_Matrix.md` · `System_Architecture_and_Solution_Design.md` · `Entity_Relationship_Diagram_and_Data_Dictionary.md` · `API_Specification.md` · `UIUX_Design_Specification.md` (core) · `UIUX_Design_Specification_v0.2.md` (companion) · `walkthrough.md` · `BASELINE_FREEZE_R3.5.md`

Precedence when two documents disagree: **BFD → BRS → RTM → SRS → SAD → ERD → API → UI/UX**. Report the conflict rather than resolving it in code.

## Governance note — read before any restore

> **Ignore the `r3.4-frozen` git tag.** It is false: it resolves to a commit that does not contain R3.4, and **0 of 16** freeze-manifest members are recoverable from it. It is not a recovery point. See `KCPC-MKT-R3.5-F1-WAIVER.md`.
>
> If predecessor reconstruction is ever required, use **`Baselines/R3.4/`**, verified with `python3 scripts/verify_r34_archive.py`.

## Working rules

- Statuses are system-controlled. Do not expose arbitrary manual status editing.
- Hold/Resume, Reschedule, Reassign, Cancel and Reopen are administrative actions, **not** workflow statuses.
- A Business Role name never automatically grants an Operational Permission. Authorization = internal access class **+** active granted permissions.
- Use soft deactivation, not destructive deletion, wherever history must be preserved.
- Any change to a requirement, workflow, schema, API contract or UI behaviour requires **controlled change management and a successor baseline** — not a code-side decision.

**Development may begin from R3.5.**

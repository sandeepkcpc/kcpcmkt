# Baseline Freeze Record — Development Baseline R3.5

# KCPC Marketing Content Production Lifecycle MVP
# Development Baseline R3.5 — FROZEN FOR IMPLEMENTATION

**Document ID:** `KCPC-MKT-BASELINE-R3.5`
**Baseline:** Development Baseline **R3.5**
**Status:** **FROZEN FOR IMPLEMENTATION**
**Predecessor:** Development Baseline **R3.4** — FROZEN
**Change Record:** `KCPC-MKT-CR-R3.5-001` — Developer-Stack Technical Architecture Realignment
**Date:** August 14, 2026
**Classification:** Confidential — Internal Use Only

---

## 1. Change Character

**Technical architecture realignment only. No business behaviour change.**

R3.5 retargets the implementation technology stack to the proven stack of the selected
developer. It carries no business change and no authorization to redesign the product.

## 2. Implementation Stack

| Layer | Technology |
| :--- | :--- |
| Runtime | **Java + Spring Boot** |
| Security | **Spring Security + signed JWT**, Secure/HttpOnly/SameSite=Lax cookie, `Path=/`, server-side token registry with revocation |
| Persistence | **Hibernate / JPA** |
| Database | **PostgreSQL 16+** |
| Frontend | **Spring MVC + JSP + HTML/CSS** (server-rendered) |
| API | **REST under `/api/v1`** |
| API documentation | **Swagger / OpenAPI** (conformant, non-authoritative) |
| Web tier | **Nginx** |
| Packaging | **Docker-compatible** |
| Style | **Modular Monolith** |

Excluded: Node.js runtime · React · MySQL conversion · microservices · `localStorage`-based
authentication tokens.

MVC and REST controllers share one application/service layer. Business logic is never
embedded in a JSP and never duplicated between the two controller families.

## 3. Business-Layer Invariance

| File | SHA-256 (working copy) | vs frozen R3.4 |
| :--- | :--- | :---: |
| `Business_Foundation_Document.md` | `857cde5be3e9c13e…` | **IDENTICAL** |
| `Business_Requirements_Specification.md` | `b05f28491feb437c…` | **IDENTICAL** |
| `Software_Requirements_Specification.md` | `74333baff9b47cb5…` | **IDENTICAL** |

Verified by direct comparison against `Baselines/R3.4/`. All R3.4 governance and
predecessor freeze records likewise remain byte-identical.

## 4. Governed Counts — Unchanged

| Entity | Count | | Entity | Count |
| :--- | :---: | :-- | :--- | :---: |
| Business Rules | **65** | | ERD constraints | **66** |
| BRS Requirements | **86** | | API IDs incl. retired `015` | **73** |
| Acceptance Criteria | **214** | | API active operations | **72** |
| SRS Requirements | **93** | | RTM rows | **86** |
| SAD Design Elements | **34** | | Internal access classes | **3** |
| SAD ADRs | **10** | | Seeded Business Roles | **17** |
| ERD active tables | **43** | | Operational Permissions | **17** |
| ERD table IDs incl. retired `040` | **44** | | Workflow concepts | **22** |

**No new identifier** — `BR`, `BRS-REQ`, `AC`, `SRS-REQ`, `SAD-DES`, `SAD-ADR`, `ERD-TBL`,
`ERD-CON`, `API-OP` or `RTM` — was created, renumbered or retired by R3.5. No business
requirement was introduced or changed. No Test Plan or Test Case artefact exists.

## 5. Audit Result

**Observed: `148/156 PASS`** — `scripts/audit_r35.py`, run against these exact bytes.

- **Non-F1 assertions: PASS.** Every specification, traceability, security, architecture,
  count and business-invariance check passes.
- **F1 git-preservation checks: 8 failing, accepted under `KCPC-MKT-R3.5-F1-WAIVER-001`.**

This is **not** a fully passing audit and is not recorded as one. The failing checks are
exactly these, and they are exclusively the F1 condition:

- F1 — repair evidence document exists
- F1 — evidence declares PASS
- F1 — evidence records the R3.4 commit, the annotated tag object and the tag commit target
- F1 — live `r3.4-frozen^{commit}` equals the R3.4 commit recorded in the evidence
- F1 — live annotated tag object equals the hash recorded in the evidence
- F1 — evidence tag commit target equals both the live tag target and the recorded R3.4 commit
- F1 — 16/16 freeze-manifest members re-verified from the tagged git tree
- F1 — 2/2 additional archive artefacts re-verified from the tagged git tree against the authoritative anchor

They were not suppressed, relaxed or reclassified. They read live git objects and will pass
on their own evidence once the tag is repaired.

## 6. Governance Exception — F1

**F1 — WAIVED FOR R3.5 IMPLEMENTATION FREEZE; TECHNICALLY UNRESOLVED.**
Recorded in `KCPC-MKT-R3.5-F1-WAIVER.md` (`KCPC-MKT-R3.5-F1-WAIVER-001`).

> **The `r3.4-frozen` Git tag is NOT authoritative and MUST NOT be used as a predecessor
> recovery source.**
>
> **`Baselines/R3.4/` remains the authoritative byte-exact predecessor preservation source
> until the Git tag is repaired.**

The archive is proven authentic by content, independently of git: **16/16 freeze-manifest
members verified, plus 2/2 additional archive artefacts independently verified = 18 files
archived.** The defect is confined to a ref name and affects governance preservation only —
it does not affect application behaviour.

Repair (`scripts/repair_f1.py`) may be performed at any time in a writable repository
environment. It changes no specification content and does not require a successor baseline.

## 7. Fingerprints

Computed from the bytes on disk by `scripts/r35_freeze_record.py`, not transcribed.

| # | File | Version / Role | EOL | Bytes | SHA-256 |
| :-: | :--- | :---: | :---: | ---: | :--- |
| 1 | `Business_Foundation_Document.md` | `v1.5.0` | CRLF | 359,492 | `857cde5be3e9c13ef3066e57b159ceef2dc840de761d4d6ffdd2350807568d56` |
| 2 | `Business_Requirements_Specification.md` | `v1.1.0` | CRLF | 239,195 | `b05f28491feb437ce2c85fedb7c4f2aeed55d984ad378f246d7d56179c8c8ef2` |
| 3 | `Requirements_Traceability_Matrix.md` | `v0.4` | LF | 136,848 | `e877eca80e4fc7b08546de5d885d5efd5820d036019046c117c88b22a930173a` |
| 4 | `Software_Requirements_Specification.md` | `v0.3` | LF | 265,204 | `74333baff9b47cb50586760c642c9e96a612a6b7d7bc0f059adc82cb804dabe3` |
| 5 | `System_Architecture_and_Solution_Design.md` | `v0.4` | CRLF | 190,050 | `aaa4dc40d623f253585d9d25af3b0161ce257c61b0496a0aae36ae1635c08f71` |
| 6 | `Entity_Relationship_Diagram_and_Data_Dictionary.md` | `v0.4` | LF | 308,373 | `f7e0074bcb8f87bbcc4fc2930c9a8ca23eb3df93c94b907d114eb3568b15af5f` |
| 7 | `API_Specification.md` | `v0.4.0` | LF | 293,762 | `a70fba1b9bf850cfc0857acf7273b647ef2a4807de423470fa1183bf29ba6530` |
| 8 | `UIUX_Design_Specification.md` | `v0.1.2` | LF | 66,667 | `62c62f7c3dcc90b69cf6cc6474e90576d0ddbdaf46cbae327c3086f10dc9e36a` |
| 9 | `UIUX_Design_Specification_v0.2.md` | `v0.2.2` | LF | 43,415 | `758dc97a8837605bab7f0fec11f50bb3e32d113edb097967a0ecd87e95bc75fd` |
| 10 | `walkthrough.md` | `Updated` | LF | 34,674 | `1cc5179f4a051f273b805b2c6c21f6bacf014c38ef7cba0d071f4028781da1a3` |
| 11 | `KCPC-MKT-CR-R3.5-001.md` | `R3.5 Change Record` | LF | 15,819 | `be4022dd135a6ed72103ec6080e611bb968ca87ffcf4449259e068b6d6f225ed` |
| 12 | `KCPC-MKT-R3.5-PRE-EDIT-IMPACT-ANALYSIS.md` | `R3.5 Pre-Edit Analysis` | LF | 13,822 | `c42257014862c072025df8d8fe327419b60fdea3597d0442e6e06e1179b3c5a1` |
| 13 | `KCPC-MKT-R3.5-FINAL-IMPACT-REPORT.md` | `R3.5 Final Impact Report` | LF | 46,737 | `95177812b1ff23c42931751f9dde17d988ab861b5b3e0506d642161f13ebf459` |
| 14 | `KCPC-MKT-R3.5-F1-WAIVER.md` | `R3.5 Governance Exception` | LF | 6,488 | `28c4050575ae26e0f27e7bd287a2ed9d934fa306c9f9af20c3e37258a7a04343` |
| 15 | `KCPC-MKT-R3.5-DEVELOPMENT-HANDOFF.md` | `R3.5 Development Handoff` | LF | 3,898 | `afc6464a542a9fc6bdcb43d62ee7aaee0a6bdb8df024d99c3e7ffbe962048559` |
| 16 | `BASELINE_FREEZE_R3.4.md` | `R3.4 freeze` | LF | 11,592 | `fc8c3784d6305ba9f94c33a2023f0f2127a9a023fccd2d8c4c95991756a99d47` |
| 17 | `BASELINE_FREEZE_R3.3.md` | `R3.3 freeze` | LF | 7,709 | `453d16e22e156e359a994ccefb1f99b8619d15649a83140164c8319677492d68` |
| 18 | `BASELINE_FREEZE_R3.2.md` | `R3.2 freeze` | LF | 11,097 | `c7e96e2d9609dda66ae592266f647674a18ebeac3a12c98e72978524165ff1b2` |

`KCPC-MKT-R3.5-FINGERPRINTS.md` and this record are **not** listed above: neither can appear
in its own manifest. The candidate manifest carries **22** entries and independently
fingerprints the specification chain, the R3.5 governance records, the F1 waiver and the
frozen R3.4 predecessor set.

## 8. What This Freeze Is, and Is Not

**It is** an implementation baseline. Development may begin against R3.5.

**It is not** stakeholder approval. Specifically:

- The **BFD remains Pending CEO Review** where that status is already recorded.
- The **BRS remains Pending Stakeholder Review** where that status is already recorded.
- This record creates no business approval and modifies no pending approval status.
- `KCPC-MKT-DR-R3.4-001` (CEO/Owner decision on the same-day Urgent boundary) remains
  authoritative provenance and is not reopened.

**Any subsequent change** to a requirement, workflow, permission, schema, API contract or UI
behaviour requires **controlled change management and a successor baseline**. R3.5 must
remain reproducible exactly as frozen.

## 9. Status Wording Precedent

Individual specification files may contain internal wording describing R3.5 as a candidate.
That wording was accurate when those exact bytes were reviewed, and editing it would break
the fingerprints recorded in §7. Following the precedent set in `BASELINE_FREEZE_R3.4.md`
§6.1: **this freeze record — not the file text — is the authoritative status statement.**

---

**Development Baseline R3.5 — FROZEN FOR IMPLEMENTATION.**

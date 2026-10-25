Result: APPROVED

# Self-review: 001-foundation

Date: 2026-10-25. Reviewer: an independent agent that did not write this code. Nothing was edited except this report.

Scope: `git diff main...HEAD` (134 commits, 271 files), read against `specs/001-foundation/` (spec, plan, tasks, data-model, contracts/openapi.yaml, quickstart), `.specify/memory/constitution.md`, `CLAUDE.md`, `docs/decision-log.md`, the agreement and every report in `docs/reports/001-foundation/` (review-tasks, security-review, review-diff-...-1328, fixes).

Counts: **0 blocking, 6 should fix, 9 minor.**

## Verify run by this reviewer

| Step | Result |
|---|---|
| `mvnw -B verify`, JDK 21.0.11 | Exit 0. Surefire and failsafe reports: 196 tests, 0 failures, 0 errors, 0 skipped |
| `npm test -- --watch=false` | 11 files, 78 tests passed |
| `npm run e2e` (port 8080 was free) | 68 passed (2.9 min). FR-028, FR-041, FR-042, FR-043 and the real FR-036 check pass |
| `scripts/check-fr-coverage.sh --strict` | 46 of 46 covered |

I did not run `scripts/verify.sh` itself from start to finish. I ran each of its steps on its own.

## 1. Tasks

- Every task is ticked except **T101** (timed checks SC-001, SC-005, SC-008 need a person) and **T104** (pull request, needs the owner's go-ahead). Both are explained in `tasks.md` and `fixes.md`.
- No `TODO`, `FIXME`, `System.out`, `printStackTrace`, `console.log` or `debugger` in `management/src/main`, `frontend/src` or `frontend/e2e`. No commented-out code found. No `@Disabled` and no `test.skip`.

## 2. Earlier findings: are the fixes real?

| Id | Checked in the code | Verdict |
|---|---|---|
| B1 | `shared` no longer imports `audit`, `security` or `masterdata` (import scan). `SensitiveMasker` is in `shared/masking`. `TableEndpoints` uses `shared.table.ExportRecorder`, which `audit/AuditExportRecorder` implements. Remaining dependencies: `security`→`audit`,`shared`; `masterdata`→`audit`,`shared`; `audit`→`shared`. No cycle | Fixed |
| S1 | `AuditService.save` calls `SensitiveMasker.sanitize(changes, SensitiveMasker.MASKED)`. `AuditMaskingIT` now passes the raw `123456789012` | Fixed (see R10) |
| S2 | `sanitize` walks nested maps and lists. New test `aPasswordNestedInOldOrNewIsNeverStored` | Fixed |
| S3 | `TableQueryService.stream` has `isolation = Isolation.REPEATABLE_READ` | Fixed, but no test (R4) |
| S4 | `shared.CurrentUserProvider` and `security.SecurityCurrentUserProvider` exist. `AuditService` has no SQL. The `@Formula` join on `users` is recorded as an accepted exception in the decision log | Fixed |
| S5 | `verify.sh` and `verify.ps1` run `npm run e2e` and stop when port 8080 is busy | Fixed |
| S6 | `TestSupportIT` no longer has `@Covers("FR-036")`. The e2e titles are retagged FR-010 and FR-020. FR-036 is covered by `e2e/fr-coverage-check.spec.ts` (it passed) | Fixed |
| S7 | T101 and T104 are `[ ]` | Fixed |
| S8 | `campus-table` shows "Could not load the list" with Try again. `AuthStore.ensureLoaded` treats only 401 as signed out. Two new unit tests | Fixed (see R13) |
| M2 | `TableRequest.forExport()` removed | Fixed |
| M3 (part) | `asSensitive()` sets `searchable=false` | Fixed. The rest is deferred, with a reason |
| M4 | Decision-log row added | Partly. The agreement, spec and data-model still disagree (R2) |
| M5 | `AuthController.changePassword` catches `UncheckedIOException`, logs the path and returns 204. New test in `ChangePasswordIT` | Fixed |
| M6 | `UserRepository.addFailedAttempt` and `pause` are single JPQL updates. New 4-thread test in `LockoutIT` | Fixed |
| M7 | `tasks.md` and `plan.md` describe the Tailwind theme | Fixed. Other PrimeNG text is still there (R5) |
| M8 | New `V5__audit_log_no_truncate.sql`; V2 is not edited. New test `theDatabaseRejectsTruncate` | Fixed |
| M9 | `@Size` on the DTOs. New test in `StandardIT` | Fixed |
| M10 | `TestProfileGuard` plus `TestProfileGuardTest` | Fixed |
| M11 | Comment added in `test-data.ts` | Fixed |
| M12 | `.idea/` untracked (the branch deletes the five files). Constitution approval recorded | Recorded, but the approval itself cannot be checked (R3) |
| M13 | Java version check in both verify scripts | Fixed |
| M1, M3 rest | Left open on purpose, with a written reason | Reason accepted |

**Were tests weakened?** I checked the test changes in every fix commit:
- `AuditMaskingIT`: now passes a raw value, which makes the test stronger.
- `TestSupportIT`: only the wrong tag was removed.
- Two e2e titles: only the FR ids changed.
- `SensitiveMaskerTest`: only moved and its import changed.

The other changes are new tests. No assertions were deleted or loosened.

## 3. FRs against the spec (checked in the code, not only the tests)

I read the implementation of FR-001 to FR-009, FR-044 and FR-045 (`AuthController`, `LoginAttemptService`, `AccountService`, `FirstRunBootstrap`, `SessionAuditListener`, `SecurityConfig`), FR-010 to FR-020 and FR-046 (services, `Versions`, `ApiExceptionHandler`), FR-021 to FR-027 (`AuditService`, `MasterDataAudit`, `AuditLogRequest`, `AuditColumns`, V2 and V5), and FR-029 to FR-035 (`TableQueryService`, `TableRequest`, `ColumnDef`, `ExcelExporter`, `TableEndpoints`). Behaviour and messages match the spec wording. Two exceptions: R1 (FR-023 for the developer reset) and R2 (the extra area).

## 4. Constitution and never-do list

- Money: none in F0. `BigDecimal` is written to Excel as text. No float or double holds money.
- Migrations: V1 to V5 are all new on this branch, and each was touched by exactly one commit. No applied migration was edited.
- Hard deletes: no people in F0. Master data deletes are refused by `ON DELETE RESTRICT`. The audit log is append-only (UPDATE, DELETE and TRUNCATE are blocked).
- Audit in the same transaction:
  - Master data writes use `Propagation.MANDATORY`.
  - The password change uses `record` inside `AccountService`'s transaction.
  - The developer reset does not (R1).
- Secrets and real data: `git ls-files` shows no `.env`, `credentials-first-run.txt`, `backups/` or `real-data/`. The test password is a committed test-only value, accepted with `TestProfileGuard`. Fixtures are synthetic.

## 5. Security

- Authentication: every `/api/**` path needs a session except `/api/auth/login`, and `/api/test-support/**` only under the `test` profile. 401 and 403 answer with JSON.
- CSRF: cookie token with the SPA handler, plus a priming filter.
- Session: the session id changes at sign-in.
- Error bodies: `server.error.include-*` is set to never. Constraint errors map to fixed messages.
- SQL: Criteria API and JPQL with parameters only. `@Formula` holds constant text. LIKE wildcards are escaped. Unknown sort and filter fields return 422.
- Input validation: `@Valid` on all bodies, `TableRequest.validate()`, and checks on the audit log's month, year and range.
- Nothing new found beyond the security-review items that were left to the owner.

## 6. Modules, naming, duplication

- Packages follow the plan (`security`, `masterdata`, `audit`, `shared`). There is no cycle.
- Dead code: R7.
- No duplicated logic that belongs in `shared`. The double masking (M1) is accepted.

## 7. Docs

See R2, R3, R5 and R6.

## Findings

### Should fix

**R1. SHOULD FIX. The developer password reset is audited after its own commit (FR-023, FR-045)**
- Location: `management/src/main/java/com/admin/management/security/FirstRunBootstrap.java`, `resetAdminPassword()`. The `transaction.execute(...)` block commits first, then `audit.recordIndependent(...)` runs in a new transaction.
- Problem: FR-023 says "if the entry cannot be saved the change MUST NOT be saved". If the audit insert fails, the password has already been replaced and the credentials file written, with no Security entry. T043 asked for `recordIndependent`, so the task text and FR-023 disagree. The password change in `AccountService` already does it correctly.
- Fix: write the reset entry inside the same `TransactionTemplate` through an `AuditService` method that takes an explicit `userId` and joins the caller's transaction (`MANDATORY`).

**R2. SHOULD FIX. The audit area "Audit log" contradicts the agreement, spec and data model**
- Location: `audit/AuditController.java` (`AREA = "Audit log"`); `frontend/src/app/features/audit/audit-log.component.ts` (`AREAS`). These conflict with the agreement's `audit_log.area` choice list "Security / Master data / Fees / Payroll / Fund" (`docs/agreement/CampusFlow_Agreement_v1.1_2026-10-24.md`, Audit Log table, Area row), with spec US4, FR-022 and FR-027, and with `data-model.md` line 94.
- Problem: the M4 fix added only a row to `docs/decision-log.md`. The agreement's section 11, the spec and the data model still list five areas. I cannot tell whether the owner chose this area or the fix agent did.
- Fix: either record audit-log exports under `Security` (no doc change needed), or, with the owner's agreement, add "Audit log" to the agreement (section 11 and the Audit Log table), the spec and `data-model.md`.

**R3. SHOULD FIX (unsure). The owner's approval of constitution 1.0.1 cannot be checked**
- Location: `.specify/memory/constitution.md:85`; `docs/decision-log.md` row "The project owner approved constitution amendment 1.0.1". Both come from commit 08596c2, made during `/fix-findings`.
- Problem: Governance requires the owner's approval. The record was written by the fix step, and I found no other sign that the owner gave it. No agent message can stand in for that approval. If the owner did approve it, this finding is closed.
- Fix: the owner confirms the approval (for example in the pull request for T104) before merge. If they have not approved it, remove the line.

**R4. SHOULD FIX. The S3 fix has no regression test**
- Location: `shared/table/TableQueryService.java`, `stream`. No test in `management/src/test` checks the snapshot. A search for `REPEATABLE`, `snapshot` and `concurrent` finds only `LockoutIT` and `TablePerformanceIT`.
- Problem: if someone removes `isolation = REPEATABLE_READ`, every test still passes. SC-007 ("exactly the rows") would quietly break for exports that run while rows are being written.
- Fix: add a `TableQueryStreamIT` case with more than 500 rows. Its sink inserts a newer row, which sorts first, in a separate committed transaction after the first chunk. Assert that no row repeats and the count matches the start.

**R5. SHOULD FIX. CLAUDE.md and the decision log do not match what was built**
- `CLAUDE.md`, Layout section: "`frontend/`: Angular app (not created yet)". It exists. Also, "Verify everything" does not say that verify now runs the browser tests and needs port 8080 free.
- `docs/decision-log.md`, row 2026-10-24 "UI direction": still says "PrimeNG with a custom preset plus Tailwind". The open item "PrimeNG theme spike (task T008)" is still listed. PrimeNG was removed (research R2, superseded).
- Fix: update the Layout line and the verify note in CLAUDE.md. Add a decision-log row that PrimeNG was dropped, and close the spike item.

**R6. SHOULD FIX. Other design docs are out of date**
- `specs/001-foundation/plan.md:85` lists `V5__table_indexes.sql (only if needed)`, but V5 is now `V5__audit_log_no_truncate.sql`. A future index migration must be V6. T087's text also names V5.
- `specs/001-foundation/data-model.md:100`: the append-only rule names only UPDATE and DELETE. The TRUNCATE trigger from V5 is missing.
- `specs/001-foundation/quickstart.md`:
  - Section 2 says "Read it once, then delete it", but FR-009 deletes the file automatically when the password is changed.
  - Section 5 omits the e2e step and the port 8080 requirement.
  - There is no timed step for SC-008 (the task review's M1 asked for one).
- `contracts/openapi.yaml:312` and the `TableRequest.java` Javadoc say the request "mirrors PrimeNG's lazy-load event".
- Fix: correct these lines.

### Minor

**R7. MINOR. Dead code.** `SensitiveMasker.withoutNeverStored` (`shared/masking/SensitiveMasker.java:35`) is used only by `SensitiveMaskerTest`. Fix: remove it and test `sanitize(changes, Set.of())` instead.

**R8. MINOR. Wrong FR in a Javadoc.** `audit/AuditExportRecorder.java` says "(FR-027)". The export entry is FR-022 and FR-033. Fix: correct the reference.

**R9. MINOR. A failing audit insert blocks logout.** In `security/AuthController.java` `logout`, `audit.recordIndependent(...)` runs before `session.invalidate()`. If the insert throws, the call returns 500 and the session stays alive, which goes against FR-006. Fix: invalidate the session and clear the context in a `finally` block.

**R10. MINOR. Masking depends on exact key names.** `SensitiveMasker.MASKED` is a fixed, case-sensitive set (`aadhaar`, `aadhaarNumber`, `bankAccount`, `accountNumber`). A later module that uses another key, for example `aadhaar_no`, would store the full value with no warning. Fix: note in the decision log or the F1 plan that new sensitive fields must be added to `MASKED`, with a test per module, or let `ColumnDef.asSensitive()` and the audit share one registry.

**R11. MINOR. Sorting on a sensitive column uses the raw value.** `TableQueryService.orders` sorts sensitive columns on the stored value, so the order can hint at hidden digits. This belongs with the deferred M3 rest. Fix: refuse sorting on sensitive columns when the "not filterable" flag is added in F1.

**R12. MINOR. Commit hygiene.** Commit 6e3a120, labelled S3, also contains the `SensitiveMasker` move that belongs to B1, and it cites FR-032 instead of FR-033 (`fixes.md` notes the second point). No action needed beyond noting it. Do not rewrite history.

**R13. MINOR (unsure, not seen in a browser). A failed first session check may leave an empty page.** `core/auth/auth.guard.ts` returns `false` when `/api/auth/me` fails with something other than 401. On the very first navigation, nothing renders except the toast, and there is no Try again. That sits close to FR-038's "never a blank screen". Fix: send the admin to a small "Cannot reach the server, Try again" page instead of returning `false`.

**R14. MINOR. Hidden columns are searched.** `ColumnDef.asHidden()` keeps `searchable`, but FR-030 says search covers the visible text columns. No F0 list uses `asHidden`, so nothing is affected today. Fix: make `asHidden()` set `searchable=false`, or reword FR-030.

**R15. MINOR. An untracked file is in the working tree.** `docs/guides/feature-workflow.md` is not on the branch. Fix: commit it on purpose or leave it out before `/push-feature`.

## Points I could not settle

- Whether the owner approved constitution 1.0.1 and the "Audit log" area (R2, R3).
- R13 is from reading the code, not from a browser test.
- I did not diff the agreement in full. I checked only the audit area and the masking rows.

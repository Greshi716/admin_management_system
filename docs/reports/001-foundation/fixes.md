# Fixes: 001-foundation

Source: `review-diff-001-foundation-2026-10-25-1328.md` (FIX FIRST). Date: 2026-10-25.

Final `bash scripts/verify.sh` (JDK 21): **VERIFY OK**. Backend 181 tests, 0 failures. Frontend 78 unit tests. Playwright 68 passed (first time it ran on this branch, so FR-028, FR-041, FR-042, FR-043 and the real FR-036 check are now confirmed). FR coverage 46 of 46.

## Fixed

| Id | What changed | Commit |
|----|--------------|--------|
| B1 | `SensitiveMasker` moved to `shared/masking`. `TableEndpoints` records exports through a new `ExportRecorder` interface in `shared`, implemented by `audit` (`AuditExportRecorder`). `shared` no longer imports `audit`. | b0afdb0 |
| S1 | `AuditService.save` now masks the values of `SensitiveMasker.MASKED` keys itself. `AuditMaskingIT` passes the raw Aadhaar value and checks the stored value is masked. | 74c3732 |
| S2 | Never-stored fields are removed at any depth of nested maps and lists. New test with `password` and `passwordHash` inside `old`/`new`. | 74c3732 |
| S3 | `TableQueryService.stream` runs with `REPEATABLE_READ`, so all export chunks see one snapshot. (Commit message says FR-032; the requirement is FR-033.) | 6e3a120 |
| S4 | New `shared.CurrentUserProvider`, implemented by `security.SecurityCurrentUserProvider`; `AuditService` no longer queries `users`. The read-only `@Formula` that shows the user name in the list is kept and recorded as an accepted exception in `docs/decision-log.md`. | bbca2cf |
| S5 | `verify.sh` and `verify.ps1` now run `npm run e2e`, and stop early with a clear message when port 8080 is busy. | f92553d |
| S6 | Removed the wrong `@Covers("FR-036")` from `TestSupportIT`; retagged the year-edit e2e test to FR-010 and the division inactive test to FR-020. FR-036 stays covered by `e2e/fr-coverage-check.spec.ts`, which `verify` now runs. | e1f6028 |
| S7 | T101 and T104 set back to `[ ]` in `tasks.md`. | 2534a95 |
| S8 | The table shows "Could not load the list" with a Try again button when a load fails (not "No rows found"). `AuthStore.ensureLoaded` treats only a 401 as signed out; guards and the sign-in page handle the other errors without redirecting. Two new unit tests. | c9cf138 |
| M2 | Removed unused `TableRequest.forExport()`. | 7cb869e |
| M3 (part) | `asSensitive()` makes the column not searchable. | 7cb869e |
| M4 | Decision-log row for the "Audit log" area. | 0473a3b |
| M7 | `tasks.md` and `plan.md` describe the Tailwind `@theme` block instead of PrimeNG; T096 points to the real files. | 2534a95 |
| M9 | `@Size(max = 100)` on names and `max = 20` on category code, refused with a message beside the field. New test in `StandardIT`. The limits are technical, not business rules; change them if the school needs longer. | 005bc0d |
| M11 | Comment in `academicYearAround` explains the April-to-March choice. | 0473a3b |
| M12 (part) | `.idea/` untracked (`git rm --cached`; files stay on disk). | 0473a3b |
| M13 | `verify.sh` and `verify.ps1` fail early when Java is below 21. | f92553d |

## Not fixed

| Id | Why |
|----|-----|
| M1 | Both places mask on purpose: `ExcelExporterTest` checks the exporter masks on its own. Removing it would mean weakening that test. Masking is idempotent, so the result is right. Left as is. |
| M3 (rest) | A filter on a sensitive column still matches the raw value. Fixing it needs a new "not filterable" flag on `ColumnDef` and the screen. No sensitive column exists in F0; do it when the first one is added (F1, Aadhaar). The export audit entry already masks filter values. |
| M5 | Touches the sign-in/password code (auth). Needs plan mode first. |
| M6 | Touches the failed-attempt counter (auth). Needs plan mode first. The review marks it unsure and not reproduced. |
| M8 | Needs a new migration (V5) for a TRUNCATE trigger (schema). Needs plan mode first. The application cannot reach TRUNCATE. |
| M10 | Changes the `test` profile guard and the test-support controller (auth config). Needs plan mode first. |
| M12 (rest) | The constitution 1.0.1 amendment needs the owner's approval to be recorded. Not mine to decide. |
| S7 (rest) | T101 (three timed checks by a person) and T104 (pull request, owner's go-ahead) are open on purpose. |

## Spec gaps

None found.

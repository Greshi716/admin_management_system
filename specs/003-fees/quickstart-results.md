# F2 fees: quickstart results

Run on 28-10-2026 on the development PC (Windows 11, JDK 21, PostgreSQL 15 in Docker), synthetic data only. Browser scenarios are covered by the Playwright specs of this branch, which start their own backend with the test profile. SC-004 and the list timings were measured on the development PC, not on the 4 GB Windows 10 school computer (carry to F11).

## Full check (T027)

`scripts/verify.sh` ended with `VERIFY OK`:

| Part | Result |
|---|---|
| Backend `./mvnw verify` | 430 tests, 0 failures, 0 errors |
| Frontend build and unit tests | 201 tests passed |
| Playwright | 138 passed |
| `check-fr-coverage.sh --strict` | 46 of 46 covered overall; 14 of 14 on branch 003-fees (FR-001 to FR-014; FR-014 by `@Covers("FR-014")` on `ModuleDependencyTest`) |

The first full run found two F1 shell specs (`students-shell.spec.ts`) that still expected the old Setup list and a ban on the word "Fees"; they were updated (Fee structure link; no paid or pending items) and the second run passed.

## Scenarios (T029)

| # | Result | Covered by |
|---|---|---|
| 1 | Pass: rows shown as 30000.00, 36000.00 (registration 500.00), 40000.50; stored scale 2; Draft tag | `fees-structure.spec.ts` (adds rows), `FeeStructureIT` |
| 2 | Pass: duplicate row, -1, 100.123 and a missing amount each refused beside the field, nothing saved | `fees-structure.spec.ts` |
| 3 | Pass: Draft row saved at once; delete asks to confirm; cancel keeps it | `fees-structure.spec.ts` |
| 4 | Pass: finalising an empty year shows "Add at least one fee row before finalising."; stays Draft | `fees-structure.spec.ts`, `FeeFinaliseIT` |
| 5 | Pass: Finalise after confirmation; status Finalised; Add, Save row and Delete gone; Finalise audit entry with counts | `fees-structure.spec.ts`, `fees-revise.spec.ts`, `FeeFinaliseIT` |
| 6 | Pass: O/S 40,000.50 + 500.00 = 40,500.50; STAFF 36,000.00 + 500.00 = 36,500.00; 28,000.00 without registration fee; Total fee column; no paid, pending or receipt items | `fees-student-views.spec.ts`, `StudentFeesIT` |
| 7 | Pass: delete through Revise; warning showed "3 students" (class records); that student then shows Fee not set; Revise audit entry | `fees-revise.spec.ts`, `FeeReviseIT` |
| 8 | Pass: Cancel at the warning changes nothing | `fees-revise.spec.ts` |
| 9 | Pass: Confirm gives 37,000.00 for the changed row's students; O/S unchanged; audit entry with old and new values and counts | `fees-revise.spec.ts`, `FeeReviseIT` |
| 10 | Pass: 409 REVISE_CONFIRMATION_REQUIRED with studentCount 3; nothing changed | `fees-revise.spec.ts`, `FeeReviseIT` |
| 11 | Pass: student registered after finalising has 36,000.00 and 36,500.00 total at once | `fees-student-views.spec.ts`, `FeeClassRecordIT` |
| 12 | Pass: 2,500.00 with note, then 1,000.50, then cleared; total due 30,500.00, 29,000.50, 28,000.00; three audit entries, note text absent; a fee-not-set student still shows it | `fees-brought-forward.spec.ts`, `BroughtForwardIT` |
| 13 | Pass: standard and category fixed with the message; division B saved | `fees-lock.spec.ts`, `FeeLockIT` |
| 14 | Pass: API change refused with 409 FEE_STRUCTURE_FINALISED, nothing changed; a year finalised after the dialog opened shows the same message | `fees-lock.spec.ts`, `FeeLockIT` |
| 15 | Pass: Draft year dialog has no notice and both fields editable; profile of an unfinalised year says "Fee structure not finalised" | `fees-lock.spec.ts`, `fees-student-views.spec.ts` |
| 16 | Pass: second save of the same row refused and the latest value shown; after finalising in one window the stale edit in the other reloads as Finalised with no warning; same on a revise | `fees-structure.spec.ts`, `fees-revise.spec.ts` |
| 17 | Pass: with no year covering today `/setup/fees` goes to Academic years | `fees-structure.spec.ts` |
| 18 | Pass: area Fees lists the finalise, the revise (36000.00 → 37000.50, reason Revise) and the brought-forward change, no note text | `audit-log.spec.ts` |
| 19 | Pass on the development PC (measured when each task was built, tagged `performance`, not part of `verify`): finalise of 2,000 class records 0.3 s, revise 0.1 s; Students list on 10,000 students: sort by Total fee at page 200 555 ms, filter 81 ms, Excel export 12.6 s | `FeeFinaliseIT`, `FeeReviseIT`, `StudentListPerformanceIT` |
| 20 | **Open, owner task T030** (timed check, under 10 minutes) | - |
| 21 | Pass: Total fee is a numeric cell, blank for Fee not set; the Fee state column holds "Fee not set" or "Not finalised" (owner decision 2026-10-28, in the decision log); no paid or pending column | `fees-student-views.spec.ts`, `StudentListIT` |
| 22 | Pass: a registration waits for a running finalise and a finalise waits for a running registration, in both orders every class record has an account; a standard change waits for a finalise and is then refused | `FeeClassRecordIT`, `FeeLockIT` |
| 23 | Pass: 28,000.00 and 28,000.50 displayed everywhere; stored scale 2; Excel cell numeric | `fees-student-views.spec.ts`, `fees-structure.spec.ts`, `StudentFeesIT` |

## Documentation (T028)

- `CLAUDE.md`: no change needed (no new command or script flag; the new test-profile endpoints are test-only).
- `docs/design/ui-prototype.md`: change log line added (fee screen is one row per standard and category, not the drawn grid).
- `docs/guides/feature-workflow.md`: F2 status updated.
- `docs/decision-log.md`: no change; the T003 spike timings confirmed the chosen method.

## Owner check (T030)

Not done yet. Enter the fee rows for 12 standards with a due date each, finalise, with a stopwatch, and record the time and pass or fail (under 10 minutes, SC-001) here.

- Time: _____ Result: _____

# Fixes: diff review round 1 (branch 003-fees)

Source: `review-diff-003-fees-2026-10-28-1727.md` (Result: FIX FIRST).

| Finding | What changed | Commit |
|---|---|---|
| S1 | First brought-forward save uses `insert ... on conflict do nothing` (`BroughtForwardRepository.insertIfAbsent`); a lost race answers STALE_VERSION with the saved value, no aborted transaction. `FeeFirstSaveRaceIT` holds the other window's insert uncommitted so the race is hit every time. | a667993 |
| S2 | Amounts with more than 10 digits before the decimal point are refused with a 422 beside the field (fee row amount and registration fee, brought forward). Tests for each, and 9,999,999,999.99 accepted. | 99b7204 |
| S3 | Not fixed by me: T030 is the owner's timed check. The owner will record the time and pass/fail in `quickstart-results.md` (lines 41 and 53-57). | - |
| S4 | Owner decision (2026-10-28): keep the Fee state column with readable text "Fee not set" / "Not finalised". `StudentTable.feeState`, `feeText`, `StudentListIT`, unit and e2e specs updated; decision logged in `docs/decision-log.md`. | ddfda3d |
| M1 | Removed unused `lockForUpdateAndBump`; `FeeRepositoryIT` now uses `lockForUpdate` + `bumpVersion`. | a667993 |
| M2 | Removed unused import. | a667993 |
| M3 | Not changed: a student's class history has one record per year, so a per-year lookup saves no query. | - |
| M4 | First fee row race: `FeeYearStatusRepository.insertIfAbsent`; a lost race answers STALE_VERSION. Test in `FeeFirstSaveRaceIT`. | a667993 |
| M5 | `parseMoney` accepts only digits with an optional decimal part (and a minus sign, so the server still says "cannot be negative"). `money.spec.ts`. | 33158fb |
| M6 | Profile Fees block: "Loading the fees…" and "The fees could not be loaded."; Brought forward section shows a message when loading fails. Unit tests for the profile. | 33158fb |
| M7 | Removed `[busy]="false"`. | 33158fb |
| M8 | Removed the stale second copy of `docs/decision-log.md`. | ddfda3d |
| M9 | Timing shown in the assertion message, no stdout. | 746d18b |
| M10 | Noted only: commit `d80a3d4` has no FR id; history is not rewritten. | - |

# Fixes after the diff review (004-fee-collection, round 1)

Source: `review-diff-004-fee-collection-2026-10-30-1310.md` (Result: FIX FIRST).

| Id | What changed | Commit |
|---|---|---|
| B1 | Owner approved the two constitution exceptions on 2026-10-30: the manual `void_receipt` data change and the Students list reading `v_fee_position` and `v_earlier_pending`. Both decision-log rows now say approved. **Not decided by the owner and still open:** Q11 (a year-fee payment that rounds to 0 days) and the on-screen day-wise totals panel row; both stay "proposed". | fix B1 |
| S1 | New `JsonMoney` test helper compares money as exact decimal text with the response's scale; all 54 `value(n.nn)` double assertions (F3 and the 8 from F2) use it. It found a real FR-023 defect: the Students list API returned `paid` as `0` and some amounts without two decimals; the list expressions now cast to `numeric(12,2)`. Two sloppy literals (`0.0`, `25000.0`) were corrected to two decimals. | fix S1 |
| S2 | The table-read scan exempts only `fees/FeeTestSupportController.java`. | fix S2 |
| S3 | `POST .../fee-collection/preview?validate=true` runs the full save checks (number, date, mode, cheque details, zero total). Save uses it, so the dialog never opens for a receipt the server will refuse. Contract updated; backend IT and e2e added (the e2e asserts no dialog). | fix S3, M1, M2 |
| S4 | `TableQueryService.stream` reads one cursor query (fetch size 500) instead of an offset query per chunk, which re-worked out the skipped rows. Students export 27.5 s to 4.3 s. No change to any money rule. | fix S4 |
| M1 | The preview returns the checked `registration` and `fee`; the dialog shows them. | fix S3, M1, M2 |
| M2 | The two e2e alert checks filter by message. | fix S3, M1, M2 |
| M3, M7, M8, M9 | FR references, duplicate-number message date ("30 Oct 2026"), imports, spec status Implemented, runbook quoting note. | fix minors |
| M4 | Print tokens `--color-print-*` in `styles.css`; the signature line is a class. | fix minors |
| M5 | `.link-target` (44px) on the receipt links and the previous-years link. | fix minors |
| M6 | Spec FR-021 now says the export entry is written right after the file, in its own transaction (as built, as F0 and F1). | fix minors |
| M9 (part) | The duplicate `## Open items` section of the decision log is not merged here (it was already so on `main`); left for a later docs change. | not fixed |

# Quickstart: validating F4 end to end

Prerequisites: JDK 21, Docker for PostgreSQL (`docker compose -f deploy/docker-compose.yml up -d`), `cd frontend && npm install`. Use only synthetic data. Stop any backend on port 8080 before the browser tests.

## Run everything

```bash
scripts/verify.sh          # backend tests, frontend build and unit tests, Playwright, FR coverage (strict)
```

## Walk-through by hand (`cd management && ./mvnw spring-boot:run`, `cd frontend && npm start`)

1. **Year copy (FR-001 to FR-005)**: with a finalised year that has divisions and fees, go to Setup, Master data, Academic years, Add year, and tick "Copy from last year". Expect the new year with the same divisions and fee rows as a draft, due dates one year later. Finalise the new year's fees.
2. **Warning (FR-014, FR-015)**: make the new year start today (or use the test profile clock). Expect a warning bar with a link to Promote students; an unpromoted student shows "Not promoted yet" in Students.
3. **Promote (FR-006 to FR-013)**: Students, pick a standard, Promote students. Untick two students, mark one Left, change one division and one category, save. Expect the Students list for the new year to show promoted students with Last year result "Promoted" and the two who stayed with "Detained".
4. **Std 10 to 11 (FR-012)**: select students, choose Science, save; choose Commerce for the rest. Expect each in the matching Std 11 standard.
5. **Std 12 (FR-009)**: the screen has no promote tick; mark some Left and leave one unticked (Detained).
6. **Carry-forward (FR-020 to FR-024)**: a student with a 12,000.00 fee and 9,000.00 paid shows 3,000.00 "brought forward" in the new year; Fee collection asks for it first; the audit log (area Fees) has the entry; the registration fee is not in it.
7. **Repeat save (FR-017)**: reload the promote page after saving; the processed students are no longer offered.
8. **Registration check (FR-027)**: in Fee collection enter less than the full registration fee; expect the refusal with the amount due.
9. **Admissions (FR-028 to FR-038)**: Approvals, Upload responses file, with the synthetic sample file created by the tests task (`management/src/test/resources/admissions/responses-sample.xlsx`). Expect the added, skipped and invalid counts, duplicates highlighted and the tab badge. Upload it again: all rows skipped. Open an entry, type a GR No., pick the year and division, approve; the student appears with source Google Form. Reject another with a remark.

## Developer correction (FR-025)

Follow `docs/runbooks/correct-promotion.md` (backup first, then `SELECT correct_promotion(<item id>, '<reason>', '<your name>')` through `psql`) on a test database. Check the audit row and that nothing was deleted.

## Expected automated results

All tests tagged FR-001 to FR-040 pass and the FR coverage check reports no uncovered requirement.

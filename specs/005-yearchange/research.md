# Research: Year Change and Admissions (F4)

Each item: decision, reason, alternatives. "Confirm" marks a point the owner should check before tasks are written.

## R1 Copying the last year (FR-001 to FR-005)

**Decision**: `YearSetupService.createWithCopy` runs in one transaction: create the year (existing rule: no overlap), then copy (a) the divisions of the source year (same standard and name, `active` kept) and (b) the fee rows (standard, category, amount, registration fee, due date + 1 year with `LocalDate.plusYears(1)`, so 29 Feb becomes 28 Feb), and create the new year's `fee_year_status` as Draft. The source is the year with the latest start date before the new year's start date. No class records, fee accounts or receipts are copied. Audit rows: one "Created" for the year (area Master data) with the counts of copied divisions, and one Fees "Create" row holding every copied fee row (standard, category, amount, registration fee, due date, unmasked as F3), so the new amounts are on record (Principle III).

**Reason**: the fee structure of F2 is per year and the admin must finalise it for fee accounts to exist, so copy-as-draft keeps the F2 flow (edit, then Finalise).

**Alternatives**: copy on a separate button after creating the year (more steps, and an empty year could be used by mistake); copy fee rows as Finalised (skips the admin's chance to edit; F2 says a finalised structure locks standard and category).

## R2 Where promotion lives and how it stays idempotent (FR-006 to FR-019)

**Decision**: new package `yearchange`. One endpoint saves one standard: `POST /api/year-change/standards/{standardId}/promote` with one line per student. The service takes a lock on the standard's source rows (`SELECT ... FOR UPDATE` on the old-year enrollments of that standard that are still `Studying`), so two saves cannot interleave. A student is processed only if their old-year record is still `Studying` and they have no record in the new year; otherwise the line is refused with a coded conflict `ALREADY_PROCESSED` and the whole save is rolled back (FR-017: all or nothing). The unique `(student_id, academic_year_id)` on `student_enrollments` is the second guard. `promotion_run_items` has a unique `from_enrollment_id`, a third guard.

**Reason**: owner wants a predictable class-by-class action; repeating the save must change nothing (SC-006).

**Alternatives**: partial saves with a per-student result list (the admin could end with half a class promoted and no easy undo); an advisory lock only (no row-level guarantee).

## R3 Carry-forward arithmetic and the one brought-forward amount (FR-020 to FR-024)

**Decision**: F2 keeps one row per student in `fee_brought_forward` (`amount`), and F3 computes the pending as `amount − valid PREVIOUS_YEARS receipts` (`v_earlier_pending`). So at promotion the new amount is `existing amount + unpaid year fee of the leaving year`, where the unpaid year fee is `pending_year_fee` from `v_fee_position` of the old enrollment (0 when the fee is "not set"). The earlier receipts keep counting against the total, so the pending that was already carried is not counted twice and nothing in F3's views changes. The registration fee is never read. If the amount is 0 no row is created or changed. The row is upserted through a new `fees.api.FeeCarryForward` that locks the student the way a receipt does (`lockStudent`), writes the Fees audit entry (student, old year, new year, old amount, new amount, unpaid year fee carried) and returns the figures for the promotion item.

Note: the old year's own pending stays visible on the old class record; it is not payable as a current-year receipt after the year ends, and the "Earlier years pending" column reads only the brought-forward row, so it is never counted twice. A left student's old-year fee is not carried (no new record); it stays pending on the old record (spec assumption, **confirm**).

**Reason**: the owner said one amount per student, paid first; this keeps F3 untouched and exact.

**Alternatives**: one row per year (contradicts the owner); recalculating the amount live from old receipts (contradicts "not recalculated"); resetting the amount to only the pending (would break the F3 rule that the amount cannot be below receipts already paid).

## R4 Next standard and stream (FR-008, FR-012)

**Decision (owner, 2026-10-31)**: Std 11 and Std 12 are each ONE standard row, so the stream is not a separate standard. The next standard is the active standard with the next higher `display_order`. The stream (Science or Commerce) becomes a field of the **yearly class record**: `V9` adds a nullable `stream` column to `student_enrollments` (a new migration; `V6` is not edited), with a CHECK that it is Science or Commerce. Rules, in the pure `PromotionRules` (unit tested):
- Promoting into Std 11 (the standard after Std 10) requires a stream for every promoted student, chosen in bulk and changeable per student.
- Promoting from Std 11 to Std 12 and detaining in Std 11 or 12 copy the stream of the old record (changeable per student).
- Std 1 to 10 have no stream; the field stays empty. Which standards take a stream is a flag `uses_stream` on `standards`, added by `V9` and set for Std 11 and 12 by the admin under Setup. **Check in the first task** that a flag is acceptable; the alternative is hard-coding Std 11 and 12.
- The existing `standards.stream` column (F0, "Science / Commerce for 11–12") is no longer used for this and stays unchanged. The fee amount is then per standard and category, the same for both streams (F2 has no stream in a fee row). The agreement's note is updated in the decision log.

The Students list and profile show the stream next to Std / Div for those standards.

## R5 Year status and the "Last year result" column (FR-013, FR-016)

**Decision**: `student_enrollments.year_status` already allows the four values. Promotion sets the old record to Promoted, Detained or Left and creates the new record as Studying. The Students list gets two read-only columns computed from that table only: **Year status** (of the shown record) and **Last year result** (the year status of the same student's record in the previous year: Promoted, Detained, or blank). Sorting or filtering by Last year result separates students who stayed (Detained) from those who arrived (Promoted). An unpromoted student's shown record is the old one, tagged "Not promoted yet" when the record's year is not the current year and its year status is Studying (FR-015).

**Reason**: no cross-module read; same sort, filter and export machinery as F1.

## R6 Warning and the Promote button (FR-006, FR-014, FR-015)

**Decision**: `GET /api/year-change/status` returns `{ active, newYear, oldYear, standards: [{ standardId, name, pending }] }`. Active only when a current year exists and a previous year (latest end before the current year's start) has `Studying` records of Active students with no record in the current year. `pending` counts those. The Promote button is enabled when `pending > 0` for that standard (and the new year is current, so "has started" is true by definition); when `pending = 0` it shows "Done". The warning bar calls the status endpoint after sign-in and after each save and is gone when the list is empty. While the new year's fee structure is Draft, promotion is refused with code `FEE_STRUCTURE_NOT_FINALISED` (assumption in the spec, **confirm**).

**Reason**: the "current year is whichever year contains today" rule means "new year has started" and "current" are the same fact, so nothing is stored.

## R7 Reading the responses file (FR-028 to FR-031)

**Decision**: accept `.xlsx` (POI, already in the project, first sheet) and `.csv` (UTF-8, comma, quotes, written by the project; Google's export). Headers are matched case-insensitively after trimming; the expected names are the registration form's field labels plus "Timestamp" and "Admission standard". Missing required header: the whole file is refused naming the headers. Limits: 5 MB and 2,000 rows; formula cells are read as values and never evaluated; the file is processed in memory and not stored. A row is invalid when a required value is missing or unreadable (name, gender, date of birth, mobile 1 of 10 digits, address, admission standard that matches an active standard by name); invalid rows are listed in the result and not imported. Dates are accepted as dates (Excel) or `dd/MM/yyyy`, `dd-MM-yyyy`, `yyyy-MM-dd` text (CSV). Re-upload: a row is skipped when a request exists with the same submission time, names and date of birth (`import_key`, unique index).

**Reason**: owner answer: Excel; the open item default allowed both, and CSV needs no library.

**Alternatives**: CSV library (new dependency for one use); storing the uploaded file (not needed, and it holds personal data).

## R8 Duplicate flag (FR-032)

**Decision**: computed at import by `DuplicateRule` (pure function plus two lookups): rule (a) same first, middle and last name (case-insensitive, trimmed) as an existing student or another request; rule (b) same names, date of birth and mobile 1. Against existing students (any status) and against other requests of status Pending or Approved. Stored as `duplicate_flag`; the review form shows which student or request matched and links to it. Never blocks.

## R9 Developer correction (FR-025)

**Decision**: `docs/runbooks/correct-promotion.md` and a function `correct_promotion(p_item_id, p_reason, p_done_by)` in `V9`, run through `psql` after a backup, never called by the app, like `void_receipt`. In one transaction it: refuses an empty reason or name; refuses when valid previous-years receipts would then exceed the reduced amount (the F3 floor); lowers `fee_brought_forward.amount` by the item's `carried_amount`; puts the old record's year status back to Studying; moves the new-year class record back to the standard, division and category given (or removes nothing: the new record is never deleted) and recalculates its fee account from the year's fee rows; inserts one `promotion_corrections` row (append-only) and one audit row (area Students, action Edit, who did it). The exact steps for each outcome (Promoted, Detained, Left) are settled in the tasks with test cases.

Class records and the brought-forward amount are mutable F1 and F2 data, not finalised records (Principle II covers receipts and payroll months). The correction restores them and leaves a `promotion_corrections` row and an audit row naming who did it (spec FR-025).

**Needs owner approval** (same as the void on 2026-10-30) and a decision-log row. Left students: the correction also sets the student back to Active only with a clear note in the runbook, since F1 says Left has no way back.

**Alternative**: an admin screen. Ruled out by the owner on 2026-10-31.

## R10 Left on the promote screen (FR-010, FR-019, FR-026)

**Decision**: Left sets the old record's year status to Left and the student's status to Left (the same state as "Mark as left" on the profile, once and for good, with the same audit). No new record, no carry-forward. No LC is created: F5 prepares it from the profile and holds the past-students record with profile and LC (clarify Q1). The promote screen shows an "LC" link per student that is hidden until F5 exists (the same approach F1 used for features not built). **Confirm** that Left on the screen also sets the student's own status, so the student leaves active lists at once.

## R11 Registration payment check in F3 (FR-027)

**Decision**: in `ReceiptRules`, when the registration amount entered is above zero, it must equal the registration fee still due (`registrationDue`). Otherwise the receipt is refused with code `REGISTRATION_PART_PAYMENT` and the message shows the amount due. A receipt with registration 0 and a year fee amount is still accepted (the admin may take the year fee first); the registration fee remains payable until the year ends and then drops off (never carried). Existing receipts are not touched; a student who already paid part of the registration under the old rule can pay the remainder in full. **Confirm** the wording "a receipt with registration 0 is still allowed" with the owner; if registration must be paid before any year fee, say so and the check becomes "refuse a year fee while registration is still due".

## R12 Approving an admission (FR-033 to FR-038)

**Decision**: `StudentService.register` today fixes the entry source to Manual and the year to the current year. A new `students.api.StudentRegistration.registerFromAdmission(StudentInput, yearId, source)` shares the same validation, GR No. check, class record, fee account hook and audit, but takes the year chosen on review (the current year or the next one) and sets source "Google Form". `AdmissionReviewService.approve` runs it in the same transaction as the request's status change (Approved, reviewer, time) and the Students audit row, so a failed registration leaves the request Pending. The admission date defaults to the review date and can be edited. Photo: not needed at approval; added from the profile (clarify Q5). The Approvals tab shows `GET /api/admissions/pending-count` and refreshes after each action and every 60 seconds.

**Alternatives**: duplicating the register code in `admissions` (two places to keep in step, which F1 forbids by design).

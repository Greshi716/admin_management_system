# Feature Specification: Fee Collection and Receipts

**Feature Branch**: `004-fee-collection`

**Created**: 2026-10-28

**Status**: Implemented

**Input**: User description: "Feature 004: Fee collection and receipts (roadmap F3; FEE-02, FEE-03 and FEE-04 as changed). The admin records fee payments for a student, prints a receipt, and sees an always-correct paid and pending balance with a 'fees paid up to' date."

**Agreement**: section 3 (FEE-02, FEE-03, FEE-04), section 4.4 (Receipts), section 5.1 (fee rules), section 8 (Fee receipt). **Decisions**: decision log row of 2026-10-28 (Receipts) and the F2 rows of 2026-10-26 (Fees). **Prototype**: Fee collection, Student profile and Students in `docs/design/ui-prototype.md`. This is feature F3; it builds on F2 (fee structure, fee accounts, brought-forward amount) and F1 (students, class records) and reuses F0 rules (current year, audit, stale-save refusal, tables, Excel export) without repeating them.

## Clarifications

### Session 2026-10-28

- Q: When the admin records a previous-years receipt, which earlier academic year does it carry, and what limits the amount, given that F2 stores only one brought-forward total per student? → A: The admin picks any academic year before the current one; the amount is limited by the student's single brought-forward pending; no "Paid up to" date is worked out for these receipts.
- Q: Must a student's brought-forward pending be fully paid before the admin can record a current-year receipt for that student? → A: Yes. A current-year receipt is refused while any brought-forward pending remains (keeps the F2 decision "payments clear earlier years' pending first").

- Q: After previous-years receipts exist, may the admin change the student's brought-forward amount to a figure below what those receipts have already paid? → A: No. The edit is refused when the new amount is below the total of the valid previous-years receipts, and the message shows that total.
- Q (plan review, owner answers of 2026-10-28): Is the receipt number unique across all receipts? → A: Yes. It is typed by the admin from the book and must stay unique. The only receipt whose number can be used again is one voided by the developer correction (see below).
- Q: Is "Fees paid up to" part of the receipt? → A: No. It is not stored on the receipt, not printed on it and not a column of the receipt register. It is shown on the student profile (and the Students list) next to the paid and remaining fees, always worked out from the current valid receipts. It is rounded to a whole day with `HALF_UP`.
- Q: What is a developer correction of a wrong receipt? → A: The developer voids it: the receipt stays on record with the status "Voided by correction", is not counted as paid, and its receipt number may be entered again on a corrected receipt.
- Q: Is a re-print marked DUPLICATE? → A: No. Every print of a receipt is identical; there is no DUPLICATE mark and no record of prints. This changes agreement section 8 and is recorded in the decision log.
- Q: What does the printed header show? → A: The school name for now; address and phone stay empty until the school gives them.
- Q (plan review, owner answers of 2026-10-28): Does "Fees paid up to" show the first unpaid day or the last paid day? → A: The last paid day: start date + days paid − 1. For a 365-day year starting 1 April, 100 days of fee paid shows 9 July.
- Q: May a receipt be dated before its academic year starts? → A: Yes. Only a date after today is refused (go-live entry of receipts already in the book).
- Q: Which class record do the Std / Div of a previous-years receipt come from? → A: The student's shown record, as the Students list shows it (the current year's, else the most recent); empty when the student has none.
- Q: How is the developer void run? → A: By the developer in the database with the function the migration provides, named in the runbook; the function refuses an empty developer name. This is the one permitted manual data change and is recorded in the decision log.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record a current-year payment and print the receipt (Priority: P1)

A parent pays at the counter. The admin opens Fee collection for the student, types the receipt number from the printed book and the receipt date, enters the registration fee and year fee amounts, picks Cash or Cheque (with cheque details), and presses Save. A dialog shows the student, receipt number, date, each amount and the total; after the admin confirms, the receipt is saved and can be printed on A4.

**Why this priority**: This is the purpose of the feature and the daily work of the admin.

**Independent Test**: With a finalised fee structure and a student with a fee, record a Cash receipt and a Cheque receipt, check the saved values, the balance after each, the audit entries and the printed page.

**Acceptance Scenarios**:

1. **Given** a student whose standard charges a registration fee and whose year fee is set, **When** the admin enters receipt no. 3100, today's date, registration fee 500.00 and year fee 10,000.00, chooses Cash, presses Save and confirms in the dialog, **Then** the receipt is saved with its own serial no., the total 10,500.00 and the total in words, and the receipt can be printed. (FR-001, FR-003, FR-008, FR-010)
2. **Given** a standard with no registration fee, **When** the form opens, **Then** it has no registration fee row. (FR-003)
3. **Given** a receipt with registration fee 0.00 and year fee 0.00, **When** the admin presses Save, **Then** it is refused beside the amounts. A receipt with only one of the two rows above zero is accepted. (FR-003)
4. **Given** receipt no. 3100 already exists, **When** the admin saves another receipt with 3100, **Then** it is refused with a message that names the existing receipt (date and serial no.) and nothing is saved. (FR-001)
5. **Given** a date in the future, **When** the admin saves, **Then** it is refused beside the date; a date in the past is accepted. (FR-002)
6. **Given** the payment mode Cheque, **When** the cheque no., cheque date or bank name is empty, **Then** the save is refused beside the empty field; for Cash those three fields are not shown. (FR-006)
7. **Given** the admin presses Save, **When** the confirmation dialog appears, **Then** it shows the student, receipt no., date, each amount and the total, and nothing is saved until the admin confirms; Cancel returns to the form with the values kept. (FR-008)
8. **Given** a saved receipt, **Then** there is no button or address to edit, cancel or delete it. (FR-009)

---

### User Story 2 - See the balance and "fees paid up to" (Priority: P1)

After each receipt, the student's profile and the Students list show total fee, paid, pending and "Paid up to". The balance is always worked out from the receipts, never from a stored total. If the admin's screen is out of date, a pop-up with a Refresh button appears.

**Why this priority**: The admin and the principal rely on this number to decide who must still pay.

**Independent Test**: Record several partial receipts for one student and check paid, pending and "paid up to" on the profile and the list; open the student in two windows, record a receipt in one and act in the other.

**Acceptance Scenarios**:

1. **Given** a student with a yearly fee of 36,500.00 in an academic year of 365 days starting 1 April, **When** receipts for 10,000.00 and then 8,250.00 are recorded, **Then** the profile's "Paid up to" is 9 July after the first (100 days paid, so 1 April + 99 days) and 30 September after the second (182.5 days rounded to 183 days paid, so 1 April + 182 days), with the remaining fees beside it; the receipts themselves show no "Paid up to". (FR-012)
2. **Given** a student whose year fee is fully paid, **Then** "Paid up to" shows "Fully paid for the year". (FR-012)
3. **Given** a student with a registration fee paid and no year fee paid, **Then** "Paid up to" shows "Nothing paid" because the registration fee is excluded. (FR-012)
4. **Given** the profile of a student with receipts, **Then** the Fees block shows year fee, registration fee, brought forward, total fee, paid, pending and "Paid up to", and a list of that student's receipts with a Collect fee button. (FR-014)
5. **Given** the Students list, **Then** it has the columns Paid, Pending, Earlier years pending, Paid up to and Fee status beside Total fee, which sort, filter and export like the other columns, and a Fee status filter. (FR-015)
6. **Given** a screen showing a balance that is older than the server's, **When** the admin presses Save on a receipt or any action that depends on the balance, **Then** a pop-up says the balance has changed with a Refresh button, and nothing is saved until the screen is refreshed. (FR-016)
7. **Given** a payment larger than what is still due for a row, **Then** it is refused with the amount still due shown. (FR-005)
8. **Given** a student with brought-forward pending above zero, **When** the admin saves a current-year receipt, **Then** it is refused with a message showing the pending still due, and the Collect fee screen offers the previous-years receipt first. (FR-005)

---

### User Story 3 - Pay an earlier year's pending (Priority: P1)

A student carries pending fee from earlier years (the brought-forward amount entered in F2). The admin records a different type of receipt, a previous-years receipt, tied to one earlier academic year, for part or all of it.

**Why this priority**: Go-live needs the old dues collected and recorded separately from the current year.

**Independent Test**: For a student with a brought-forward amount, record a previous-years receipt, then check that brought forward, paid and pending change, that the current-year amounts do not, and the printed receipt.

**Acceptance Scenarios**:

1. **Given** a student with a brought-forward amount, **When** the admin chooses the receipt type Previous years' pending, picks the earlier academic year, enters an amount and confirms, **Then** the receipt is saved as that type, tied to that year, and the brought-forward pending falls by that amount. (FR-004)
2. **Given** a student with no brought-forward amount, **Then** the previous-years receipt type is not offered. (FR-004)
3. **Given** an amount above what is still due of the brought-forward amount, **Then** it is refused with the amount still due shown. (FR-005)
4. **Given** a student with valid previous-years receipts totalling 3,000.00, **When** the admin edits the brought-forward amount to 2,500.00, **Then** it is refused with a message showing 3,000.00; 3,000.00 or more is accepted. (FR-025)
5. **Given** a previous-years receipt, **Then** its printout shows one row for the previous years' fee and the chosen academic year, instead of the registration fee and year fee rows. (FR-004, FR-017)

---

### User Story 4 - Mark a cheque "not cleared" (Priority: P2)

A cheque is returned by the bank. The admin opens that cheque receipt and marks it "Cheque not cleared" with a reason. The amount is added back to the student's pending and the paid-up-to date is recalculated.

**Why this priority**: Receipts cannot be cancelled, so this is the only controlled reversal.

**Independent Test**: Record a Cheque receipt, mark it, and check the balance, "Paid up to", the receipt's status, the audit entry, that it cannot be marked twice, and that a new receipt can pay the amount again.

**Acceptance Scenarios**:

1. **Given** a Cheque receipt with status Valid, **When** the admin opens it, presses Cheque not cleared, enters a reason and confirms, **Then** the status becomes "Cheque not cleared", the amount is added back to pending, the student's "Paid up to" is recalculated, and an audit entry records the receipt, reason and the amounts. (FR-013, FR-021)
2. **Given** no reason, **Then** the action is refused beside the reason field. (FR-013)
3. **Given** a Cash receipt, **Then** the action is not offered. (FR-013)
4. **Given** a receipt already marked, **Then** the action is not offered again and the mark cannot be undone; paying again needs a new receipt with a new receipt number. (FR-013)
5. **Given** a marked receipt, **Then** it stays in the receipts list and on the profile with its status, is not counted as paid, and its details are unchanged. (FR-009, FR-013)

---

### User Story 5 - Print and re-print a receipt (Priority: P2)

The admin prints a receipt on A4 right after saving it, and can open it later from the student's profile or the receipts list and print it again; a re-print is identical to the first print.

**Why this priority**: The parent takes the printed receipt, and the school may need another copy.

**Independent Test**: Save and print a receipt of each type, print it again from the list, and check that the content of both prints is the same.

**Acceptance Scenarios**:

1. **Given** a saved current-year receipt, **When** the admin prints it, **Then** the A4 page shows the school name, receipt no., date, student name, GR no., std and division, the registration fee and year fee rows, the mode and cheque details (for Cheque), the total, the total in words and a signature line, and no "Paid up to" date. (FR-017)
2. **Given** a receipt that was already printed, **When** it is printed again, **Then** the page is identical to the first print, with no DUPLICATE mark. (FR-018)
3. **Given** a Cheque not cleared receipt or a receipt voided by correction, **When** it is printed, **Then** the page shows that status. (FR-017)

---

### User Story 6 - Receipt register and Excel export (Priority: P2)

The admin opens the list of all receipts, filters by date range, mode, standard and status, sees day-wise totals, and exports the result to Excel for the books.

**Why this priority**: The school reconciles the cash book and cheques against receipts every day; the fund ledger (F9) will read the same receipts.

**Independent Test**: Record receipts of both modes on several dates, filter by each criterion, and compare the on-screen and Excel totals with the receipts entered.

**Acceptance Scenarios**:

1. **Given** receipts of several dates, modes and standards, **When** the admin filters, **Then** the list shows only matching receipts, with receipt no., serial no., date, student, GR no., std / division, type, mode, amounts, total and status. (FR-019)
2. **Given** a filter, **When** the admin presses Export to Excel, **Then** the file has the same rows and filters, amounts as numeric cells with two decimals, and a day-wise totals section, and an audit entry exists. (FR-020, FR-021)
3. **Given** receipts marked Cheque not cleared, **Then** they are listed with their status and are not counted in any total. (FR-019, FR-020)

---

### Edge Cases

- A student with no fee for the year ("Fee not set" or "Fee structure not finalised"): a current-year receipt is refused with a clear message; a previous-years receipt is still possible (FR-007).
- A student marked Left can still pay pending fees (FR-007).
- Go-live entry of receipts already given in the book: the previous-years receipts must be entered before the current-year receipts of the same student, because the brought-forward pending must be cleared first; the admin enters the book's receipts in that order (FR-005).
- A student with no class record in the current year: only a previous-years receipt can be recorded (FR-007).
- Two windows save a receipt for the same student at once, so that together they would overpay: the second is refused as stale (FR-016, FR-005).
- A revise in F2 lowers the year fee below what was paid: pending is shown as zero and the difference as "excess paid"; no refund (F2 FR-004; FR-014).
- The yearly fee is zero: there is nothing to pay and "Paid up to" shows "Fully paid for the year" (FR-012).
- A year fee payment smaller than half a day's fee rounds to zero days: "Paid up to" shows the start date, not "Nothing paid" (FR-012; to be confirmed by the owner, plan open question Q11).
- A cheque dated in the future or the past is accepted as typed; the school decides when to deposit it (FR-006).
- A receipt number typed with letters, spaces or leading zeros: the admin types it from the book, so the rule is in the Assumptions.
- A cheque is marked not cleared after later receipts were recorded: the profile's "Paid up to" is recalculated from the remaining valid receipts (FR-012, FR-013).
- A wrong receipt (wrong student, wrong amount) is found after saving: it cannot be fixed in the application; the developer voids it by the runbook and the admin enters a corrected receipt, which may use the same receipt number (FR-001, FR-022).

## Requirements *(mandatory)*

Each requirement names the user story and the agreement or decision it comes from.

### Functional Requirements

**Receipt entry**

- **FR-001** *(US1; FEE-02, decision log 2026-10-28)*: Admin MUST type the receipt number from the printed book. The number MUST be unique among all receipts, including receipts marked Cheque not cleared; the only number that can be used again is that of a receipt voided by the developer correction (FR-022). A duplicate MUST be refused with a message naming the existing receipt (date and serial no.). System MUST add its own serial no., a running number that is never reused.
- **FR-002** *(US1; decision log 2026-10-28)*: Admin MUST enter the receipt date on the form. A date in the past is accepted (entry of earlier receipts at go-live); a date after today MUST be refused.
- **FR-003** *(US1; decision log 2026-10-28)*: A current-year receipt MUST have a registration fee row (only for a student whose fee account charges a registration fee) and a year fee row. Each amount MUST be zero or more; at least one MUST be above zero.
- **FR-004** *(US3; decision log 2026-10-28)*: A previous-years receipt MUST pay the student's brought-forward pending for one earlier academic year and MUST be stored as a different receipt type tied to that year. It MUST have one amount row and no registration fee row. The admin MUST pick the academic year from the years before the current one; the amount is limited only by the student's single brought-forward pending (F2 keeps no breakdown by year), so the chosen year is a label on the receipt and does not change the limit. A previous-years receipt has no "Paid up to" date, because the student's fee for an earlier year is not held anywhere.
- **FR-005** *(US2, US3; decision log 2026-10-28)*: System MUST refuse an overpayment: each amount is limited to what is still due for its row (registration fee due, year fee due, brought-forward pending), worked out from the valid receipts. The refusal MUST show the amount still due. There is no instalment schedule; any amount above zero may be paid at any time within a receipt type. Earlier years' pending is paid first (F2 FR-007): a current-year receipt MUST be refused, with a message that shows the brought-forward pending still due, while the student's brought-forward pending is above zero; a previous-years receipt is always allowed up to that pending. A cheque marked not cleared (FR-013) on a previous-years receipt adds the amount back to the brought-forward pending, which blocks new current-year receipts again; current-year receipts already saved stay valid.
- **FR-025** *(US3; F2 FR-007)*: Once valid previous-years receipts exist for a student, the F2 edit of the brought-forward amount MUST be refused, on the screen and on the server, when the new amount is below the total of those valid receipts; the message MUST show that total. A cheque marked not cleared lowers that total. The F2 audit entry of the change stays as in F2.
- **FR-006** *(US1; FEE-02)*: Payment mode MUST be Cash or Cheque and nothing else. For Cheque, cheque no., cheque date and bank name MUST be required; for Cash they are not asked.
- **FR-007** *(US1, US2; decision log 2026-10-28, F2 FR-005)*: A current-year receipt MUST be refused, with a clear message, when the student's current-year fee is "Fee not set" or the fee structure is "not finalised", and when the student has no class record in the current year. A student marked Left MAY still pay pending fees. A previous-years receipt does not need a current-year fee.
- **FR-008** *(US1; decision log 2026-10-28)*: Before a receipt is saved, a confirmation dialog MUST show the student, receipt no., date, each amount and the total. Nothing is saved until the admin confirms. This dialog is the only safeguard against a wrong receipt (FR-022).
- **FR-009** *(US1, US4; FEE-04, constitution II)*: A saved receipt MUST NOT be edited, cancelled or deleted, in the screen or on the server; no such action exists. Marking a cheque not cleared (FR-013) MUST be kept as a separate record, so that the receipt's own values never change.
- **FR-010** *(US1; agreement 4.4)*: Each receipt MUST store: receipt no., serial no., receipt date, academic year, student, the standard and division at the time of receipt (for a previous-years receipt those of the student's shown class record, empty when there is none), payment mode and cheque details, the amounts, the total, the total in words, the receipt type, and who recorded it and when. A receipt does not hold a "Paid up to" date.
- **FR-011** *(US1; decision log 2026-10-28)*: The total in words MUST be in English in the Indian system (thousand, lakh, crore) and include paise when there are any. For example 1,25,000.50 reads "One Lakh Twenty-Five Thousand Rupees and Fifty Paise Only".

**Paid up to**

- **FR-012** *(US2; decision log 2026-10-28)*: System MUST work out "Paid up to", the last day that is paid, from the start date of the academic year: days paid = total year fee paid ÷ (yearly fee ÷ days in the year), rounded to a whole day with `HALF_UP`; "Paid up to" = start date + days paid − 1 day. When some year fee is paid but it rounds to less than one day, the date is the start date. The registration fee MUST NOT count. The total year fee paid is the total of the student's valid current-year receipts, so each new receipt continues from the previous total. The date MUST be shown on the student's profile and in the Students list, beside the remaining fees, and MUST NOT be stored on, printed on or listed with a receipt; it is always worked out from the current valid receipts. When the year fee is fully paid it shows "Fully paid for the year"; when no year fee is paid it shows "Nothing paid". Days in the year = end date − start date + 1 of the academic year; the last day of the year is the latest date shown (days paid never exceeds the days in the year). Previous-years receipts do not enter the calculation (FR-004).
- **FR-013** *(US4; FEE-04, decision log 2026-10-28)*: Admin MUST be able to open a Cheque receipt and mark it "Cheque not cleared" with a required reason, after a confirmation. The amount MUST be added back to the student's pending, the student's "Paid up to" MUST be recalculated from the remaining valid receipts, the change MUST be audited (FR-021), and the receipt MUST stay on record with the status Cheque not cleared, never counted as paid. The action is one way: it cannot be undone, and a Cash receipt, a receipt already marked or a receipt voided by correction cannot be marked. The receipt number stays used. A cheque that is paid again gets a new receipt and a new receipt number.

**Balances and screens**

- **FR-014** *(US2; FEE-03, prototype Student profile and Fee collection)*: The student profile's Fees block MUST add paid, pending, total fee (year fee + registration fee + brought forward), "Paid up to", the student's receipts with status, and a Collect fee button. The Collect fee screen MUST show the balance before and after the amounts typed. If a fee revise has put the fee below the amount paid, pending is zero and the difference is shown as "excess paid". The earlier hiding of receipts, paid and pending (F2 FR-009) ends.
- **FR-015** *(US2; FEE-03, prototype Students)*: The Students list MUST add the columns Paid, Pending, Earlier years pending, Paid up to and Fee status (Paid, Partly paid, Unpaid) beside F2's Total fee, and a Fee status filter, all sorted, filtered and exported with the page (F0). Total fee stays the year fee plus the registration fee of the shown record; Paid and Pending are for that record's year; Earlier years pending is the brought-forward amount minus the valid previous-years receipts; Fee status is Paid when neither Pending nor Earlier years pending is above zero, Partly paid when something is paid and something is pending, Unpaid when nothing is paid and something is pending. F2's hiding of these columns ends. A student with no fee shows "Fee not set" or "Not finalised" and no amount.
- **FR-016** *(US2; FEE-03)*: The balance shown MUST always be worked out from the valid receipts, never from a stored running total. When a save or an action depends on a balance that has changed on the server since the screen loaded (another window, another receipt), System MUST refuse it and the screen MUST show a pop-up with a Refresh button; after Refresh the admin sees the latest balance.

**Printing**

- **FR-017** *(US1, US3, US5; agreement section 8, decision log 2026-10-28)*: A receipt MUST print on A4 in English only. The first draft layout follows the school's receipt book: school name, receipt no., date, student, GR no., std and division, the rows of the receipt type (registration fee and year fee, or the previous years' fee), the mode and the cheque details, total, total in words and a signature line; no "Paid up to" date. A Cheque not cleared or voided receipt also prints its status. The header shows the school name (address and phone are shown only when configured). The layout will be revised later with a sample from the school; the content above MUST NOT depend on the layout.
- **FR-018** *(US5; owner decision 2026-10-28, changes agreement section 8)*: A re-print MUST be identical to the first print: no DUPLICATE mark, and the system keeps no record of prints. The change of agreement section 8 ("Duplicate copies are marked DUPLICATE") MUST be recorded in the decision log and in the agreement's section 11.

**Register and export**

- **FR-019** *(US6; agreement section 8)*: The receipts list MUST show all receipts (Finance, Fee collection) with receipt no., serial no., date, student, GR no., std / division, type, mode, registration, year and previous-years amounts, total and status (Valid, Cheque not cleared, Voided by correction). It MUST filter by date range, mode, standard and status, search by receipt no. or student, and open a receipt to view, print or mark.
- **FR-020** *(US6; F0 export rules)*: Admin MUST be able to export the receipt register to Excel with the filters in force. The file MUST have the same rows as the list, amounts as numeric cells with two decimals, and day-wise totals (per date: count, cash, cheque, total). Receipts marked Cheque not cleared are listed with their status and excluded from the totals.

**Audit and money**

- **FR-021** *(all stories; constitution III, F0 FR-021 to FR-025)*: Every receipt creation and every Cheque not cleared mark MUST write an audit entry in the area Fees in the same transaction as the change, and every receipt export MUST write one in the area Fees as soon as the file is produced, in its own transaction as in F0 and F1 (the export entry records the filters and the row count). A receipt entry records the receipt no., serial no., student, type, mode and each amount and the total in full; a mark entry records the receipt, the reason, the amount added back and the student's "Paid up to" before and after (worked out, not stored on the receipt). Money fields in these entries MUST be shown in full, not masked; the rule MUST be recorded in the decision log, closing the open item "Audit masking for money fields" for fee receipts. If the entry cannot be saved, the change MUST NOT be saved.
- **FR-022** *(US1; decision log 2026-10-28)*: A developer-assisted correction runbook MUST be added at `docs/runbooks/correct-receipt.md`, in the style of the Admin password reset runbook, for a wrong receipt that cannot be fixed in the application: when to use it, who may do it, the backup taken first, the steps, what is recorded, and the check afterwards. The correction is a void, not an edit: the wrong receipt stays on record with the status "Voided by correction", is not counted as paid, its receipt number may be entered again on the corrected receipt, and its serial no. is never reused. An audit entry in the area Fees records the void, the reason and who did it. The runbook MUST NOT remove the audit trail, and the application itself offers no way to void.
- **FR-023** *(all stories; constitution I)*: Every amount MUST be an exact decimal with two places, shown with two places (for example 10,500.00). An amount with more than two places or a negative amount MUST be refused, not rounded. Any calculation in this feature MUST be exact; where rounding is needed, `HALF_UP` applies.

**Testing**

- **FR-024** *(all stories; constitution VIII)*: Every requirement in this specification MUST have at least one automated test that references its ID. Calculation tests MUST include the worked "Paid up to" cases of FR-012 (leap year, zero fee, full payment, nothing paid, rounding at the last day, a cheque not cleared) and the total-in-words cases of FR-011.

### Key Entities

- **Receipt**: One payment: receipt no. (typed), serial no. (system), date, academic year, student, class snapshot, type, mode, cheque details, amounts, total, total in words, recorded by and on. Never changed or deleted.
- **Receipt Mark**: A separate record that a receipt no longer counts as paid: Cheque not cleared (by the admin, Cheque receipts only) or Voided by correction (by the developer). Reason, marked by and on, the amount added back. One at most per receipt, never undone.
- **Student Fee Balance**: Not stored. Worked out from the fee account (F2), the brought-forward amount (F2) and the valid receipts.
- **Audit Entry**: Reused from F0, area Fees.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** *(FR-001 to FR-010)*: An admin can record a receipt, including the confirmation, in under 1 minute, and print it in one more action.
- **SC-002** *(FR-005, FR-012, FR-016)*: In 100% of tested receipt sequences (partial, full, past-dated, cheque not cleared) paid, pending and "Paid up to" match the figures worked out by hand from the receipts.
- **SC-003** *(FR-001, FR-002, FR-003, FR-006)*: In 100% of tested invalid receipts (duplicate number, future date, both amounts zero, missing cheque detail, overpayment) nothing is saved and a message beside the field says why.
- **SC-004** *(FR-009)*: In 100% of tested cases a saved receipt cannot be edited, cancelled or deleted from the screen or the server, and its stored values are identical after a cheque is marked not cleared.
- **SC-005** *(FR-011)*: In 100% of tested amounts (including 0.50, 1,00,000.00 and 12,34,56,789.05) the total in words is correct.
- **SC-006** *(FR-019, FR-020)*: The Excel register and its day-wise totals equal the sum of the valid receipts in the filter in 100% of tested filters.
- **SC-007** *(FR-021)*: 100% of receipt creations, marks and exports have a matching audit entry, and no entry exists for a change that failed.
- **SC-008** *(FR-015, FR-019)*: The Students list and the receipts list with 2,000 students and 10,000 receipts open and filter in under 2 seconds on the development PC (the target computer is checked in F11).
- **SC-009** *(FR-024)*: Every functional requirement is covered by at least one passing test.

## Assumptions

- The brought-forward amount entered in F2 is the total of earlier years' dues; the pending shown here is that amount minus the valid previous-years receipts, and F2's edit of it is limited by FR-025.
- F2 is built first. This feature changes F2's rule that paid, pending and receipts stay hidden (F2 FR-009, FR-010) and uses F2's fee account, brought-forward amount and finalised structure.
- The agreement's `status` and `unclear_reason` fields on a receipt are held as the separate Cheque Not Cleared Mark, so a receipt row never changes (FR-009, constitution II, CLAUDE.md "Never change a finalised receipt"). The receipt list and print show the status as if it were a field.
- "Paid up to" is only a figure of the student (profile and Students list), worked out when it is shown; no receipt holds it (owner answer 2026-10-28).
- A receipt date earlier than the start of the academic year is not specially restricted. The receipt's academic year is the current academic year for a current-year receipt, and the chosen earlier year for a previous-years receipt.
- The receipt number is typed as the book shows it, up to 20 characters, compared ignoring case and spaces at the ends.
- A receipt can be printed any number of times from the screen right after saving, from the student's profile or from the receipt list; all prints are the same (FR-018).
- Constitution Principle II mentions "receipt cancellation with a reason" as an example correction. The owner's decision of 2026-10-28 replaces it with the cheque-not-cleared mark and the developer void (FR-022); both are reversal entries that leave the receipt unchanged, so no amendment is expected; the plan's Constitution Check confirms it.
- Because a voided receipt frees its receipt number, the corrected receipt may carry the same number; a cheque-not-cleared receipt does not free its number.
- The students module must not import fees internals: the Students list columns and the profile read balances through a shared interface, or through the read-only list-column exception of constitution VII, which the plan must name in Complexity Tracking and the decision log. The fees module owns the receipt tables, added by a new Flyway migration (the next free version).
- Receipts are in rupees only, one admin user, no roles; "recorded by" is the signed-in admin.
- Receipt tests use synthetic students and amounts only (constitution VI).
- Out of scope: the fund ledger (F9 reads receipts), fee reminders (F10), refunds, concessions, late fees, instalment schedules, payment modes other than Cash and Cheque, cancelling, editing or deleting a receipt, receipt layout in Gujarati, and a final receipt layout (waits for the school's sample).
- Open items waiting for the client are not specified here.

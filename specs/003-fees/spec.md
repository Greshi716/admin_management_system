# Feature Specification: Fee Structure and Student Fee Accounts

**Feature Branch**: `003-fees`

**Created**: 2026-10-26

**Status**: Draft

**Input**: User description: "Fee structure and student fee accounts (agreement ADM-02, FEE-01, FEE-05, section 4.4 Student Fee Account, section 5.1 fee rules). The admin enters and finalises a year's fee structure, can revise it with a warning, and every student's class record gets its fee from it. Pending fees of earlier years are entered on the student's profile."

**Agreement**: section 4.2 (Fee Structure), section 4.4 (Student Fee Account), section 5.1 (fee rules). **Decisions**: decision log rows of 2026-10-26 (Fees). **Prototype**: Setup: fee structure, Students and Student profile in `docs/design/ui-prototype.md`. This is feature F2; it builds on F1 (students and yearly class records) and reuses F0 rules (current year, audit, stale-save refusal, tables, Excel export) without repeating them.

## Clarifications

### Session 2026-10-26

- Q: When the category row has no registration fee but the standard's blank-category row has one, which registration fee is charged? → A: The category row's if it has one, otherwise the blank-category row's; if neither has one, none is charged.
- Q: In a revise, are all edits saved together after one confirmation, or is each row changed and confirmed on its own? → A: Each row change is its own revise, with its own warning, confirmation, recalculation and audit entry.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enter and finalise a year's fee structure (Priority: P1)

The admin opens Setup, Fees and salary, Fee structure, picks an academic year (the current year is shown first) and enters the rows: standard, category (blank means all categories of that standard), yearly amount, optional registration fee and due date. The structure stays a Draft that can be changed freely until the admin finalises it after a confirmation.

**Why this priority**: No student can be given a fee until a structure exists, and F3 (receipts) needs a fixed fee to collect against.

**Independent Test**: Open Fee structure for a year, add, edit and delete rows, finalise, and check that the rows can no longer be changed in the ordinary way and that an audit entry exists for every step.

**Acceptance Scenarios**:

1. **Given** a year with no fee rows, **When** the admin adds a row with standard, yearly amount and due date and saves, **Then** the row appears, the year shows the status Draft, and an audit entry (area Fees) is written. (FR-001, FR-012)
2. **Given** a Draft structure, **When** the admin edits or deletes a row, **Then** the change is saved at once with no warning other than the delete confirmation. (FR-002)
3. **Given** a row for Std 6 with a blank category, **When** the admin adds another row for Std 6 with a blank category in the same year, **Then** the save is refused with a message beside the field. (FR-001)
4. **Given** a Draft structure with at least one row, **When** the admin presses Finalise and confirms, **Then** the status becomes Finalised and the rows can no longer be added, edited or deleted from this screen. (FR-002, FR-003)
5. **Given** a Draft structure with no rows, **When** the admin presses Finalise, **Then** it is refused with a message. (FR-002)
6. **Given** an amount with more than two decimal places or a negative amount, **When** the admin saves, **Then** it is refused beside the field and nothing is rounded silently. (FR-001, FR-011)

---

### User Story 2 - Each student gets a fee from the structure (Priority: P1)

When a year's structure is finalised, every class record of that year gets its yearly fee, registration fee (if any) and due date from the structure. A student whose class record is created later in that year gets the fee at once. The student's profile and the Students list show the result.

**Why this priority**: This is the purpose of the feature: a fixed fee per student that F3 collects against.

**Independent Test**: With students in several standards and categories, finalise a structure and check each student's fee, including a student with a category row, a student that falls back to the all-categories row, and a student with no row.

**Acceptance Scenarios**:

1. **Given** a student whose class record has standard Std 10 and category O/S, and rows for Std 10 O/S and Std 10 (blank), **When** the year is finalised, **Then** the student's fee is the Std 10 O/S amount. (FR-005, FR-006)
2. **Given** the same rows but a student in category STAFF, **Then** the student's fee is the Std 10 blank-category amount. (FR-005)
3. **Given** a student whose standard has no row at all, **Then** the profile shows "Fee not set" and no amount is invented; the Students list shows "Fee not set". (FR-005, FR-009, FR-010)
4. **Given** a standard whose row has a registration fee, **Then** every student in that standard (new or existing) is charged it, shown as a separate amount from the yearly fee. (FR-005)
5. **Given** a finalised year, **When** the admin registers a student or creates a class record for that year, **Then** the fee account is created at once from the structure. (FR-006)
6. **Given** the profile of a student with a fee, **When** it opens, **Then** a Fees block shows the year's fee, registration fee (if any), brought-forward amount, total due and due date, and shows no receipts, paid or pending amounts. (FR-009)
7. **Given** the Students list, **When** it opens, **Then** it has a Total fee column and no paid, pending or fee-status columns. (FR-010)

---

### User Story 3 - Revise a finalised structure (Priority: P2)

A mistake is found, or the school changes a fee, after finalising. The admin presses Revise and changes the structure one row at a time (edit, add or delete a row). Before each row change is saved, the admin is told that every student's fee for the year will be recalculated and how many students that is, and must confirm explicitly.

**Why this priority**: Real life needs corrections, and the agreement allows them only through this controlled action.

**Independent Test**: Finalise a structure with students, revise one amount, check the warning shows the right student count, confirm, and check every affected fee account and the audit entry.

**Acceptance Scenarios**:

1. **Given** a Finalised structure, **When** the admin presses Revise, **Then** rows can be edited, added and deleted one at a time and the screen says the year is being revised. (FR-004)
2. **Given** a change to one row, **When** the admin presses Save (or Delete), **Then** a warning states that every student's fee for the year will be recalculated, shows the number of students affected, and that row is not changed until the admin confirms. (FR-004)
3. **Given** the admin confirms, **Then** that row is saved, every class record of that year gets its fee, registration fee and due date recalculated, and one audit entry records the old and new values of that row and the number of students recalculated. A second row change repeats the warning, recalculation and audit entry. (FR-004, FR-006, FR-012)
4. **Given** the admin cancels at the warning, **Then** that row is not changed. (FR-004)
5. **Given** a revise, **Then** receipts that already exist (from F3 on) are never changed. (FR-004)

---

### User Story 4 - Enter pending fees of earlier years (Priority: P2)

For a student with dues from earlier years, the admin edits the profile and enters a brought-forward amount with a short note. It counts in the student's total due.

**Why this priority**: Go-live needs the dues of earlier years; the school has no import file.

**Independent Test**: Edit a student, enter a brought-forward amount and note, and check the profile's Fees block, the audit entry, and a later correction of the amount.

**Acceptance Scenarios**:

1. **Given** a student, **When** the admin enters a brought-forward amount and a note on the edit screen and saves, **Then** the Fees block shows it and the total due includes it. (FR-007, FR-009)
2. **Given** a student with a brought-forward amount, **When** the admin changes or clears it, **Then** the new value is saved and an audit entry records the old and new amount and note. (FR-007, FR-012)
3. **Given** a student with a brought-forward amount but no fee for the year (fee not set), **Then** the Fees block still shows the brought-forward amount. (FR-007, FR-009)

---

### User Story 5 - Class cannot change after finalising (Priority: P2)

Once a year's structure is finalised, the standard and category of that year's class records are fixed, so no fee can silently change. The division can still be changed.

**Why this priority**: It protects the fee amounts that F3 will collect against.

**Independent Test**: Finalise a year, then try to change a student's standard, category and division for that year.

**Acceptance Scenarios**:

1. **Given** a finalised year, **When** the admin opens the class record change form for that year, **Then** standard and category are shown as fixed with a clear message that the year's fee structure is finalised, and division can still be changed. (FR-008)
2. **Given** a finalised year, **When** a change of standard or category for that year is sent anyway, **Then** it is refused with the same message and nothing is changed. (FR-008)
3. **Given** a year whose structure is Draft or does not exist, **Then** standard and category can be changed as in F1. (FR-008)

---

### Edge Cases

- No current academic year: the fee screens are blocked by the F0 rule; the admin is sent to create a year first (FR-013).
- A year with a Draft structure: students of that year show "Fee structure not finalised" instead of a fee; no amount is invented (FR-006, FR-009).
- A student registered after finalisation in a standard with no row: the fee account shows "Fee not set"; adding the missing row through Revise gives the student the fee (FR-004, FR-005).
- A standard, category or academic year used by a fee row cannot be deleted (F0 FR-020); an inactive standard or category is not offered for new rows but stays on existing rows (FR-001).
- A student marked Left keeps the fee account of the year and is counted in the recalculation (FR-004, FR-006).
- Two windows edit the same row or structure: the second save is refused (FR-013).
- A wrong standard or category found after finalising cannot be corrected in the application; the agreement allows no unlock (FR-008).
- A revise that deletes a row used by students: those students show "Fee not set" (or fall back to the standard's blank-category row) after the confirmation (FR-004, FR-005).
- A revise that lowers a fee below what a student has already paid (possible only once F3 exists): the difference is shown as excess paid (FR-004).

## Requirements *(mandatory)*

Each requirement names the user story and the agreement or decision it comes from.

### Functional Requirements

**Fee structure**

- **FR-001** *(US1; FEE-01, FEE-05, ADM-02, agreement 4.2)*: Admin MUST be able to enter the fee structure of one academic year as rows, each with standard (required), category (optional; blank means all categories of that standard), yearly amount (required), registration fee (optional) and due date (optional). The year's rows MUST hold at most one row per standard and category (one blank-category row per standard). Amounts are zero or more, with at most two decimal places. Inactive standards and categories are not offered for new rows but stay on existing rows.
- **FR-002** *(US1; decision log 2026-10-26)*: A year's structure MUST be Draft until the admin finalises it. While Draft, rows MUST be addable, editable and deletable freely. Finalising MUST ask for confirmation and MUST be refused when the structure has no rows.
- **FR-003** *(US1; decision log 2026-10-26)*: After finalising, the screen MUST NOT allow ordinary add, edit or delete of rows (screen and server). The only way to change a finalised structure is Revise (FR-004).
- **FR-004** *(US3; agreement 5.1, decision log 2026-10-26)*: Admin MUST be able to revise a finalised structure, one row at a time (edit, add or delete a row), each row change being its own revise. Before each row change is saved, the screen MUST warn that every student's fee for the year will be recalculated, show the number of students affected (every student with a class record in that year), and save only after explicit confirmation. A confirmed row change MUST recalculate every class record of that year (FR-006) and MUST NOT change any receipt. If a revised fee falls below the amount a student has already paid, the difference MUST be shown as "excess paid"; there is no refund logic in version 1 (applies once receipts exist in F3).

**Which fee applies**

- **FR-005** *(US2; agreement 5.1, decision log 2026-10-26)*: For a class record, System MUST use the year's row for the record's standard and category; if there is none, the row for the standard with a blank category; if there is neither, the fee is "Fee not set" and no amount is invented. The registration fee MUST be charged to every student in a standard that has one, new or existing, and shown separately from the yearly fee. It is taken from the row that applies to the student (standard and category) if that row has a registration fee, otherwise from the standard's blank-category row; if neither has one, none is charged. A student detained in a standard with a registration fee pays it again the next year, because it is charged per standard per year. There is no late fee, concession, caution money, mid-year admission or per-student change of an amount.

**Student fee account**

- **FR-006** *(US2, US3; agreement 4.4)*: Each class record of a finalised year MUST have a fee account holding the yearly fee, registration fee (if any) and due date taken from the structure. The accounts MUST be created when the year is finalised, created when a class record is added to a finalised year, and recalculated by every confirmed revise (row change). A class record of a year that is not finalised has no fee account and shows "Fee structure not finalised".
- **FR-007** *(US4; agreement 5.1, decision log 2026-10-26)*: On the student's edit screen the admin MUST be able to enter, change or clear one brought-forward amount (zero or more, two decimals) with an optional short note, for the dues of earlier years. It is one amount per student with no breakdown by year, it counts in the student's total due, and it is never changed by a finalise or revise. Decided: payments will clear earlier years' pending first; this is built in F3, and F2 only records it.

**Lock**

- **FR-008** *(US5; agreement 5.1, decision log 2026-10-26)*: Once a year's structure is finalised, System MUST refuse a change of standard or category of any class record of that year (screen and server). Division can still be changed. The F1 class record change form MUST show standard and category as fixed with a clear message instead of failing. Years that are Draft or have no structure keep the F1 rules.

**Screens**

- **FR-009** *(US2, US4; prototype Student profile)*: The Student profile MUST show a Fees block for the year of the class record it shows: yearly fee (or "Fee not set" / "Fee structure not finalised"), registration fee (if any), brought-forward amount, total due (yearly fee + registration fee + brought-forward) and due date. It MUST NOT show receipts, paid or pending amounts, or Collect fee; those come with F3.
- **FR-010** *(US2; prototype Students)*: The Students list MUST add a Total fee column (yearly fee + registration fee of the record shown; "Fee not set" or "Not finalised" when there is none), also in the Excel export. Paid, pending and fee-status columns and the Fees filter stay hidden until F3.
- **FR-013** *(all stories; F0 FR-013, FR-046, FR-028)*: The fee structure screen MUST sit under Setup, Fees and salary, in the F1 shell with the current purple colours, following the prototype's Setup: fee structure card (title with the year, one line of help, save and finalise actions). It MUST refuse to work without a current academic year as in F0, and MUST refuse a stale save of a row or structure as in F0. Parts of the prototype whose feature is not built (Copy from last year, Salary rules, Facilities and deductions, Leave types, Backup and restore, Excel import) stay hidden.

**Money and audit**

- **FR-011** *(all stories; constitution I)*: Every fee amount MUST be held as an exact decimal with two places and shown with two places (for example 28,000.00). An amount with more than two places or a negative amount MUST be refused, not rounded. Any calculation in this feature (totals) MUST be exact; where a rounding is needed later, Excel `ROUND` halves-up applies.
- **FR-012** *(all stories; constitution III, F0 FR-021 to FR-025)*: Every fee row create, edit and delete, every finalise, every revise and every change of a brought-forward amount MUST write an audit entry in the new area Fees in the same transaction, with old and new values. A finalise entry records the number of fee accounts created; each revise (one row change) writes one entry that records the old and new values of the row and the number of students recalculated. If the entry cannot be saved the change MUST NOT be saved. Fee amounts and notes are not sensitive fields and are shown in full; sensitive fields keep the F0 masking.

**Testing**

- **FR-014** *(all stories; constitution VIII)*: Every requirement in this specification MUST have at least one automated test that references its ID.

### Key Entities

- **Fee Structure Row**: One row of a year's structure: academic year, standard, category (or blank for all), yearly amount, registration fee, due date.
- **Fee Structure Status**: Per academic year, Draft or Finalised.
- **Student Fee Account**: One per class record of a finalised year: the yearly fee, registration fee and due date taken from the structure, or "Fee not set".
- **Brought-forward Amount**: One amount and a short note per student for dues of earlier years.
- **Audit Entry**: Reused from F0, with the new area Fees.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** *(FR-001, FR-002)*: An admin can enter the rows for a school with 12 standards and finalise them in under 10 minutes.
- **SC-002** *(FR-005, FR-006)*: After finalising, 100% of tested class records (category row, fallback row, no row, registration-fee standard) show the correct fee, and none shows an invented amount.
- **SC-003** *(FR-004)*: In 100% of tested row changes during a revise the warning shows the correct number of students, nothing changes before confirmation, and afterwards every class record of the year matches the new structure.
- **SC-004** *(FR-006)*: Finalising a year with 2,000 class records, and each confirmed row change of a revise, completes in under 10 seconds on the target school computer (Windows 10, 4 GB RAM).
- **SC-005** *(FR-008)*: In 100% of tested cases a change of standard or category in a finalised year is refused and the F1 form explains why before the admin submits.
- **SC-006** *(FR-011)*: In 100% of tested cases amounts are stored and shown with exactly two decimals and invalid amounts are refused.
- **SC-007** *(FR-012)*: 100% of fee row, finalise, revise and brought-forward changes have a matching audit entry, and no entry exists for a change that failed.
- **SC-008** *(FR-014)*: Every functional requirement is covered by at least one passing test.

## Assumptions

- F1 (students and yearly class records, the new shell) is built first. F2 changes F1's class record edit rule (FR-008) and adds to its list and profile.
- A year's structure can be entered and finalised for any academic year, not only the current one; the screen starts on the current year.
- The prototype shows fees as a grid (a column per category and one due date per standard). This spec uses one row per standard and category, as the agreement's Fee Structure does, with a due date and registration fee on each row; the card, title and buttons follow the prototype.
- "Students affected" in the revise warning counts every class record of that year, including students marked Left, because all of them are recalculated.
- The list's Total fee is the yearly fee plus the registration fee; the profile's total due also adds the brought-forward amount.
- The brought-forward note is optional, up to 200 characters.
- The "excess paid" rule (FR-004) and the payment order (FR-007) concern F3 behaviour; F2 only records the decisions (confirmed by the owner on 2026-10-26).
- `affects_fees` on categories stays a hint only and does not decide which categories get rows.
- Out of scope: receipts, payments, balances, "fees paid up to" date, reminders (F3 and later), copying a structure to a new year and year rollover (F4), reports and dashboards (F10).
- Open items still waiting for the client are not specified here.
- `docs/decision-log.md` open item "Audit masking for money fields" is settled for fees by FR-012 (amounts in full); F5 and F8 still decide for salary.

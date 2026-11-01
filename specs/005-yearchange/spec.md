# Feature Specification: Year Change and Admissions

**Feature Branch**: `005-yearchange`

**Created**: 2026-10-31

**Status**: Draft

**Input**: User description: "Year change and admissions (F4, includes the former F1b). Agreement IDs: YR-01, YR-02, YR-03, STU-02. New academic year setup, promotion, fee carry-forward and Google Form admissions."

**Agreement**: section 3 (YR-01, YR-02, YR-03, STU-02), section 4.3 (Yearly class record, Google Form admissions), section 4.4 (Fee account), section 6 (workflows). **Decisions**: decision log rows of 2026-10-31 and the F4 open items (owner answers; they take priority). **Prototype**: Admission approvals in `docs/design/ui-prototype.md` (Approvals tab with a count badge).

## Clarifications

### Session 2026-10-31

- Q: What does the Std 12 promote screen offer, since there is no next standard? → A (owner): Students who finish Std 12 or leave at any time are marked Left and kept in a record of past (left) students that holds their profile and their LC. The screen therefore has both actions: Promote (Std 1 to 11) and Mark as Left / LC. Std 12 students are either marked Left or left unticked (Detained, repeating Std 12). The past-students record and the LC itself are built in F5 (agreement: Left Students Register); F4 only sets year status Left and keeps the profile.

- Q: What do the Students list and fee collection show for students not yet promoted, and when does the warning clear? → A (owner): Option A, with a note: unpromoted students are tagged "Not promoted yet" and are to be moved into the new year's records with the other students, a student who stays back being handled like a failed student (Detained). Fee collection waits for promotion: the old year's unpaid fee is carried at promotion and paid then as brought forward (decided 2026-10-31 after the plan found F3 cannot take an old-year payment before promotion). The warning clears when every standard is done.
- Q: Should F3 refuse a registration payment smaller than the full registration fee? → A (owner): Yes (Option A). F3 refuses it; part payments are allowed on the year fee only.
- Q: Which match flags a form row as a possible duplicate? → A (owner): Option A. Either the same first, middle and last name, or the same name, date of birth and mobile, against existing students and other waiting entries. The flag highlights only and never blocks.
- Q: When is the photo of a form admission added? → A (owner): Option A. Later from the student profile (F1's upload); approval does not need it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Set up the new academic year by copying the last one (Priority: P1)

Before the new school year starts, the admin creates the new academic year (name, start date, end date) and chooses to copy the setup of the last year: its standards' divisions and its fee amounts (yearly fee, registration fee and due date for every standard and category). Due dates move forward by one year. The admin then edits the copied divisions and fee amounts as needed and finalises the fees as in F2.

**Why this priority**: Promotion needs divisions and fees in the new year to exist, so nothing else can start without it.

**Independent Test**: With a finalised year, create the next year with "copy from last year" and check that every division and fee row exists in the new year with due dates one year later, that the old year is unchanged, and that edits to the copy do not touch the old year.

**Acceptance Scenarios**:

1. **Given** a year with divisions and finalised fee amounts, **When** the admin creates the next year and chooses to copy, **Then** the new year has the same divisions and the same fee amounts (as a draft), and every due date is exactly one year later.
2. **Given** the copied setup, **When** the admin changes a fee amount or adds or removes a division in the new year, **Then** the old year's divisions and fees are unchanged.
3. **Given** a new year that has been created, **When** its start date arrives, **Then** it becomes the current year with no "close old year" step, and the old year stays on record.
4. **Given** the admin chooses not to copy, **When** the year is created, **Then** it is created empty as today.

---

### User Story 2 - Promote the students of a standard (Priority: P1)

Once the new academic year has started, each standard shows a "Promote students" button. The admin opens the promote screen for a standard, sees its students (all ticked), unticks any who stay back, and saves. Ticked students move to the next standard (the one next in display order) in the new year and keep their division letter; unticked students stay in the same standard and division in the new year as "Detained". Per student the admin may change the division or the category (a category change changes the fee) or mark the student Left. After saving, the Students list filtered by a standard shows the students who stayed under their own standard separately from those who arrived by promotion.

**Why this priority**: This is the core of the year change and the daily value of the feature.

**Independent Test**: Start the new year, promote one standard with some students unticked, one Left, one with a changed division and one with a changed category; check each student's new class record, year status, fee, and the Students list.

**Acceptance Scenarios**:

1. **Given** the new year has started, **When** the admin opens a standard, **Then** "Promote students" is enabled; **and Given** the new year has not started, **Then** it is disabled.
2. **Given** a standard with 30 students, all ticked, **When** the admin saves, **Then** each student gets a class record in the next standard in the new year with the same division letter, and the old record's year status is Promoted.
3. **Given** two students unticked, **When** the admin saves, **Then** they get a class record in the new year in the same standard and division, and the old record's year status is Detained.
4. **Given** a student marked Left on the promote screen, **When** the admin saves, **Then** the old record's year status is Left, the student gets no class record in the new year, and no leaving certificate is created (it is prepared later from the profile).
5. **Given** a promoted student whose division or category is changed on the screen, **When** the admin saves, **Then** the new class record has the chosen division and category, and the student's fee for the new year follows the new category.
6. **Given** the promote screen of Standard 10, **When** the admin selects students and chooses Science or Commerce and saves, **Then** those students are promoted to Standard 11 with that stream; students without a stream chosen cannot be saved as promoted.
7. **Given** a standard already promoted, **When** the admin opens the Students list filtered by that standard for the new year, **Then** the students who stayed are listed separately from the promoted ones.
8. **Given** the same standard was already promoted, **When** the admin opens the promote screen again, **Then** students already processed are not offered a second time.

---

### User Story 3 - Warning until promotion is done (Priority: P2)

When the new academic year has started and promotion is not done, the app shows a warning with a message and a link to the Promote students page. The warning stays until promotion is done.

**Why this priority**: It prevents the admin from forgetting promotion, which would leave students without a class record in the current year.

**Independent Test**: Start the new year with unpromoted students; check the warning and link appear on screens, then promote and check the warning disappears.

**Acceptance Scenarios**:

1. **Given** the new year has started and a standard still has unpromoted students, **When** the admin opens any main screen, **Then** a warning with a message and a link to the Promote students page is shown.
2. **Given** promotion is done, **When** the admin opens any screen, **Then** the warning is gone.
3. **Given** the new year has not started, **When** the admin opens any screen, **Then** no warning is shown.

---

### User Story 4 - Carry unpaid year fee forward at promotion (Priority: P1)

When the admin saves a promotion, the unpaid year fee of the year the student leaves becomes the student's single brought-forward amount in the year the student moves to. This also applies to students who stay back. The amount is set at that moment and not recalculated afterwards. The registration fee never carries forward. In fee collection the brought-forward amount is paid first and is highlighted as "brought forward"; then the current year's fee follows. Every carry-forward is audited. Students with pending fees can still be promoted, marked Left and get a leaving certificate (the LC shows a warning only).

**Why this priority**: It is money. A wrong figure breaks the school's accounts, and the owner's rules are specific.

**Independent Test**: Take students with a part-paid year fee, a fully paid year fee, an unpaid registration fee, and an existing brought-forward amount; promote and check each student's brought-forward amount, the audit entries, and that fee collection asks for the brought-forward first.

**Acceptance Scenarios**:

1. **Given** a student with a year fee of 12,000.00 of which 9,000.00 is paid, **When** promoted, **Then** the student's brought-forward amount in the new year is 3,000.00, highlighted as brought forward, and an audit entry records who, when, the old and the new values.
2. **Given** a student with an unpaid registration fee, **When** promoted, **Then** the registration fee is not carried forward.
3. **Given** a student who stays back (unticked) with a pending year fee, **When** saved, **Then** the pending year fee is carried into the new year as brought forward.
4. **Given** a student whose year fee is fully paid, **When** promoted, **Then** no brought-forward amount is created.
5. **Given** a student with an earlier brought-forward amount of 5,000.00 of which 2,000.00 is paid, and an unpaid year fee of 3,000.00, **When** promoted, **Then** the student has one brought-forward amount of 8,000.00, 2,000.00 of it paid, so 6,000.00 is pending, and not one amount per year.
6. **Given** a carried-forward amount, **When** later receipts are entered in the old year or the fee structure changes, **Then** the carried amount does not change.
7. **Given** a brought-forward amount and the new year's fee, **When** the admin collects a fee, **Then** the brought-forward amount must be paid first, as in F3.
8. **Given** a student with pending fees marked Left, **When** the admin saves, **Then** the save is not blocked.

---

### User Story 5 - Review Google Form admissions on a waiting list (Priority: P2)

The admin downloads the form responses as an Excel file and uploads it on the Approvals page ("Upload responses file"). Each row becomes a waiting-list entry with status Pending. Possible duplicates are highlighted. The admin opens an entry, corrects the details, types the GR No., chooses the academic year, division and (if the form has none) the category, and approves, which creates the student and the class record and the fee account. Or the admin rejects it with a remark. The top bar shows an Approvals tab with the number of Pending entries.

**Why this priority**: From now on admissions go through the form, but promotion has no dependency on it, so it can follow.

**Independent Test**: Upload a synthetic responses file with new, duplicate and invalid rows; check the waiting list, the highlight, an approval (student and class record created) and a rejection, and the badge count.

**Acceptance Scenarios**:

1. **Given** a responses file with the expected columns, **When** the admin uploads it, **Then** each valid row becomes a Pending entry with its submission time, the form data and an import batch number, and the admin sees how many were added, skipped and invalid.
2. **Given** a file missing expected columns, **When** uploaded, **Then** nothing is imported and the message names the missing columns.
3. **Given** a row that looks like an existing student or another waiting entry, **When** imported, **Then** it is flagged as a possible duplicate and highlighted.
4. **Given** a Pending entry, **When** the admin corrects fields, enters a GR No. not used by any student, picks the year and division and approves, **Then** a student is created with source "Google Form", a class record for the chosen year, a fee account as for manual entry, and the entry becomes Approved with the reviewer recorded.
5. **Given** a GR No. already used, **When** the admin approves, **Then** approval is refused and the entry stays Pending.
6. **Given** a Pending entry, **When** the admin rejects it with a remark, **Then** it becomes Rejected, no student is created, and the entry stays on record.
7. **Given** Pending entries exist, **When** the admin looks at the top bar, **Then** the Approvals tab shows the count; **and** it updates after each approval or rejection.
8. **Given** a student entered by hand in F1, **When** a form row for the same person arrives, **Then** the row is flagged as a duplicate and the admin decides.

---

### Edge Cases

- No previous year exists to copy from: the copy option is not offered.
- The new year's dates overlap an existing year: refused, as today.
- The copied due date falls on 29 February: it moves to 28 February or 1 March in the target year (assumed 28 February).
- A division in the old year has no equivalent letter in the new year (the admin removed it): promoted students of that division must be given a division before saving.
- A promoted student has no fee for the new standard and category: promotion of that student is blocked with a message naming the missing fee (fee structure must be finalised first).
- The new year's fees are still in draft: promotion is refused until finalised, since carried amounts and category fees depend on it. [assumed; see Assumptions]
- A student already has a class record in the new year (for example a form admission made early): that student is not offered for promotion.
- The admin saves a promotion twice (double click or reload): the second save changes nothing and does not carry forward twice.
- A student is promoted by mistake: corrected by a developer-run reversing correction, with an audit entry; the correction never deletes a class record, receipt or fee row.
- The responses file is empty, is in the wrong format, or has a row without a name, date of birth, mobile, address or standard: row is reported as invalid and not imported; valid rows still import.
- The same file is uploaded twice: rows already imported are skipped, not added again.
- Standard 12: it has no next standard (see FR-009).
- A year in which nothing has started yet: no warning and the Promote button is disabled.

## Requirements *(mandatory)*

### Functional Requirements

**New year setup (YR-01)**

- **FR-001**: When the admin creates an academic year, the system MUST offer to copy the setup of the last year (the year with the latest start date before the new one): its divisions per standard and its fee amounts (yearly fee, registration fee, due date) for every standard and category. The copy MUST be created in draft and MUST NOT change the source year.
- **FR-002**: The system MUST shift every copied due date forward by one year.
- **FR-003**: The admin MUST be able to edit the copied divisions and fee amounts in the new year with the F0 and F2 screens before finalising.
- **FR-004**: The system MUST NOT have a "close old year" step. The current year MUST stay the year whose dates contain today (school date), as already decided.
- **FR-005**: Creating a year, copying and every later edit MUST be audited (area Master data or Fees as for existing screens), with before and after values.

**Promotion (YR-02)**

- **FR-006**: Each standard MUST have a "Promote students" action, enabled only when the new academic year has started (the next year after the one the students are in is the current year). While disabled, it MUST say why.
- **FR-007**: The promote screen MUST list the standard's students with a class record in the old year and none in the new year, all ticked by default, with select-all and untick-all.
- **FR-008**: On save, each ticked student MUST get a class record in the new year in the next standard (next higher display order among active standards) with the same division letter, and the old record's year status MUST be set to Promoted. Each unticked student MUST get a class record in the new year in the same standard and division, and the old record's year status MUST be set to Detained.
- **FR-009**: The promote screen of the last standard (Std 12) MUST have no promote tick. Each Std 12 student MUST be either marked Left (year status Left, no new class record, kept in the past-students record of F5 with profile and LC) or left unticked (Detained: same standard and division in the new year, pending year fee carried forward). On every standard's promote screen the admin MUST also have a per-student link to prepare the LC (opens the F5 LC from the profile; the LC and the past-students record are built in F5, not F4).
- **FR-010**: For each student on the promote screen the admin MUST be able to change the new division and the new category, and to mark the student Left. A student marked Left MUST get year status Left on the old record and no new class record; no leaving certificate is created by this feature. The LC is prepared later from the profile (F5).
- **FR-011**: A category change on the promote screen MUST change the student's fee for the new year to the fee set for the new standard and category.
- **FR-012**: Std 11 and Std 12 are each one standard; the stream (Science or Commerce) is stored on the student's yearly class record. Promoting Standard 10 to Standard 11 MUST require a stream for every promoted student, chosen in bulk by selecting students and saving; stream MUST also be changeable per student. Promoting Std 11 to Std 12, and detaining in Std 11 or 12, MUST keep the old stream unless the admin changes it. Standards 1 to 10 have no stream.
- **FR-013**: The Students list filtered by standard MUST show students who stayed in the standard (Detained) separately from those promoted into it, and MUST show the year status.
- **FR-014**: While the new year has started and promotion is not done, the system MUST show a warning with a message and a link to the Promote students page, on the main screens, until it is done.
- **FR-015**: Until a student is promoted, the Students list MUST show the student's most recent record tagged "Not promoted yet", with a note to move the student into the new year through the promote screen, where they join the other students of the new year (promoted, or Detained like a student who failed). Fee collection for a student not yet promoted MUST show "Not promoted yet: promote the student first" and take no receipt; the old year's unpaid fee is carried at promotion and paid then as brought forward. The warning MUST list the standards still to do and clear when every standard is done.
- **FR-016**: Year status values MUST be Studying, Promoted, Detained and Left. A new class record starts as Studying.
- **FR-017**: Saving a promotion MUST be one all-or-nothing action per standard: either every student is processed or none, and repeating the save MUST NOT create duplicate class records or carry-forwards.
- **FR-018**: Every promotion MUST be audited per student (area Students): who, when, old and new standard, division, category, stream and year status.
- **FR-019**: A student with pending fees MUST NOT be blocked from promotion, being left behind, or being marked Left.

**Fee carry-forward (YR-03)**

- **FR-020**: When a promotion is saved, the system MUST add the unpaid year fee of the year the student leaves (year fee minus valid year-fee receipts) to the student's single brought-forward amount. Receipts for earlier years keep counting against that total, so what the student still owes is the amount minus those receipts. This applies to promoted and detained students. Left students have no class record in the new year, so nothing is carried for them. [assumed; see Assumptions]
- **FR-021**: The registration fee MUST NOT carry forward.
- **FR-022**: The carried figure MUST be fixed at promotion and MUST NOT be recalculated from later changes; the stored amount changes only by a later promotion or by the existing F2 brought-forward edit. Amounts MUST be exact (NUMERIC(12,2) and BigDecimal).
- **FR-023**: In fee collection the brought-forward amount MUST be paid before the current year's fee (the F3 rule is kept) and MUST be shown highlighted as "brought forward" on the profile, in fee collection and on printed receipts as F3 does for earlier years.
- **FR-024**: Every carry-forward MUST be audited, in the area Fees, with who, when, student, old year, new year, the old unpaid amount and the new brought-forward amount, in the same transaction as the write.
- **FR-025**: A wrong promotion or carry-forward MUST be correctable only by a developer-run correction (documented runbook) that writes a correction record and an audit entry in one transaction. The correction may restore a class record's year status, move the new-year class record back, and lower the brought-forward amount by the carried figure. It never deletes a student, class record, receipt or fee row, never changes a receipt, and refuses a change that would take the brought-forward amount below the valid previous-years receipts.
- **FR-026**: A student with pending fees MUST still be able to receive a leaving certificate; the LC shows a warning only (decision of 24 Oct 2026; LC issuing itself is F5).
- **FR-027**: F3 MUST refuse a registration payment smaller than the full registration fee still due; part payments are allowed on the year fee only. This is the one change F4 makes to F3 receipts. The change MUST be recorded in the decision log, and existing receipts are not altered.

**Google Form admissions (STU-02)**

- **FR-028**: The admin MUST be able to upload the downloaded responses file on the Approvals page ("Upload responses file"); the system MUST NOT fetch anything from Google automatically.
- **FR-029**: The file MUST have the registration form's fields except GR No. and Photo. The required columns are the ones in FR-030; Category, Admission date and Timestamp are optional columns. A file missing a required column MUST be refused entirely with a message naming the missing columns. Both Excel (.xlsx) and CSV (.csv) are accepted. [assumed; see Assumptions]
- **FR-030**: Each valid row MUST create a waiting-list entry (submission time, form data, import batch number) with status Pending. An invalid row (a required field missing or unreadable: surname, first name, gender, date of birth, mobile 1, address or admission standard) MUST NOT be imported and MUST be listed in the upload result with its row number and reason; valid rows still import.
- **FR-031**: A row already imported (same submission time, name and date of birth) MUST be skipped when the same or an overlapping file is uploaded again, and the upload result MUST say how many were skipped.
- **FR-032**: A row MUST be flagged as a possible duplicate, and highlighted, when either (a) first, middle and last name match, or (b) name, date of birth and mobile match, an existing student or another waiting entry. The flag MUST only highlight and MUST NOT block approval.
- **FR-033**: The admin MUST be able to open an entry, correct any field, type the GR No., choose the academic year and division (and the category if the form gave none), and approve or reject. The standard comes from the form.
- **FR-034**: Approving MUST create the student (source "Google Form") and the class record and fee account exactly as a manual F1 registration does, refuse a GR No. already used, and mark the entry Approved with the reviewer and time. Rejecting MUST require a remark and mark the entry Rejected. Entries are never deleted.
- **FR-035**: Approved and Rejected entries MUST NOT be changed again. Approval and rejection MUST be audited (area Students).
- **FR-036**: The top bar MUST show an Approvals tab with the number of Pending entries, as in the prototype, updated after each action.
- **FR-037**: The Photo is added by the admin and is not in the file. It MUST be added later from the student profile, and approval MUST NOT need it.
- **FR-038**: Admissions entered by hand through F1 MUST keep working unchanged.

**General**

- **FR-039**: All screens MUST be English only, for the single Admin user, with no role-based access.
- **FR-040**: No real student or salary data MUST be used in tests, fixtures or examples; sample files MUST be synthetic.

### Key Entities

- **Academic year**: a school year with start and end dates; the current one is whichever contains today. Gains a "copied from" setup action, not a stored status.
- **Yearly class record**: one student, one year, a standard, division, category, optional stream and year status (Studying / Promoted / Detained / Left). Promotion creates the new year's record and sets the old one's status.
- **Brought-forward amount**: the single unpaid amount a student carries into a year, fixed at promotion, shown separately from the year fee, paid first.
- **Promotion run**: one saved promotion of a standard, listing every student's outcome; the basis for audit and for the developer-run correction.
- **Admission request**: a waiting-list entry from the form (submission time, form data, import batch, possible-duplicate flag, status Pending / Approved / Rejected, reviewer, remarks).
- **Import batch**: one upload of the responses file with its counts (added, skipped, invalid).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The admin can create the next academic year with its divisions and fee amounts in under 5 minutes, without retyping any amount that stays the same.
- **SC-002**: The admin can promote a standard of 40 students, with a few exceptions, in under 5 minutes, and the full school (all standards) in under one hour.
- **SC-003**: 100% of students are either promoted, kept back or marked Left after saving a standard; none are left without a class record or year status.
- **SC-004**: For every student promoted, the carried figure equals the unpaid year fee of the old year to the paisa, and the pending brought-forward equals the amount minus previous-years receipts, checked against the agreement's reference cases and the 5,000.00 / 2,000.00 / 3,000.00 case; the registration fee never appears in either.
- **SC-005**: Every promotion and every carry-forward has a matching audit entry; none are missing in a full-school promotion test.
- **SC-006**: Saving the same promotion twice leaves the data identical to saving it once.
- **SC-007**: A responses file of 200 rows is imported and shown on the waiting list in under 30 seconds, and uploading the same file again adds no new entries.
- **SC-008**: In a test set containing known duplicates, every duplicate is highlighted, and no approval creates a second student with the same GR No.
- **SC-009**: The admin can review and approve a typical form entry in under 2 minutes.

## Assumptions

- A single Admin user uses one school computer; no cloud, no role-based access.
- The next standard is the active standard with the next higher display order. Division letters are matched to the new year's divisions of the target standard.
- Unticked students are recorded as Detained and stay in the same standard and division in the new year (decision of 2026-10-31).
- The new year's fee structure should be finalised before promotion, because carried amounts and category fees depend on it; promotion for a student with no matching fee is blocked with a message. This is an assumption to confirm.
- A student marked Left on the promote screen carries no amount into the new year, because there is no class record in it; their unpaid year fee stays on the old year and is still visible as pending, and the LC warns about it. To confirm.
- Due dates falling on 29 February move to 28 February in a non-leap year.
- Upload: both .xlsx and .csv accepted; Google's Timestamp column read when present; rows already imported (same timestamp, name and date of birth) skipped on re-upload. Open item for clarify.
- For a new admission the standard comes from the form; at review the admin picks the academic year (the current year or the next one when admitting early), the division, and the category if the form has none. Open item for clarify.
- F3 receipts are not changed by this feature except the registration-fee check in FR-027.
- The correction of a wrong promotion is a developer-run runbook, not a screen.
- Out of scope: the Std 12 leaving flow beyond marking Left, LC issuing (F5), automatic fetching from Google, fee reminders, reports.

# Feature Specification: Students and Class Records

**Feature Branch**: `002-students`

**Created**: 2026-10-25

**Status**: Draft

**Input**: User description: "Students and class records (agreement STU-01, STU-03, STU-04). The admin registers students by hand, keeps a yearly class record per student, and finds students in a list with a View profile action. Includes restyling the app shell to the UI prototype."

**Agreement**: section 4.3 (Students, Yearly Class Record). **Prototype**: Students and Student profile screens in `docs/design/ui-prototype.md`. This is feature F1.

## Clarifications

### Session 2026-10-25

- Q: When the admin marks a student Left, what happens, and do Left students show in the default list? → A: Plain status change (no date or reason); Active students only in the default list, Left shown through the Status filter; a Left student cannot be set back to Active.
- Q: Which year status does this feature set on a class record? → A: Studying on creation; marking a student Left does not change it; Promoted and Detained come from F4, Left from F5.
- Q: How do the student's category and the yearly class record's category relate, and which decides the fee? → A: One category is asked at registration, stored on the student and copied to the first class record; later changes to a year's category change only that year's record; the yearly category is the one fees use.
- Q: How are Aadhaar and religion/caste shown? → A: Masked (last 4 visible) on the list, profile and exports; the profile shows both; the full value appears only in the edit form.
- Q: Can the admin correct a GR No. after saving? → A: Yes, audited with old and new value; the old number stays reserved and can never be given to a student.

### Owner decisions after the spec review (2026-10-25)

- The list shows the student's most recent class record, with its year in small text, when there is none for the current year.
- Aadhaar and religion/caste are stored as plain text (no field-level encryption) and are masked wherever they are shown, except in the edit form.
- A future admission date is refused.
- The profile has a plain Mark as left button (status change only) and no prepare-LC button.
- Edits of a student and of a class record are protected against stale saves.
- Mobile numbers are shown in full everywhere, including the profile.
- The list, the profile heading and the class history show the yearly (current) category. Changing a class record is rare, so it needs no more than a simple change action.
- The profile shows the audit history of that student's data (changes to the student and to the class records).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register a student (Priority: P1)

The admin types a new student into the application: GR No. from the school register, names, gender, date of birth, parents, mobile numbers, address, category, admission date and standard, and the class for the current academic year. An optional photo can be added. Many existing students will be typed in, so the screen must be quick to use from the keyboard, with "Save and add next".

**Why this priority**: Nothing else in CampusFlow (fees, leaving certificates, promotion) works without students. There is no Excel import, so manual entry is the only way existing students get in.

**Independent Test**: Sign in, open Register student, fill the required fields, save, and see the student in the list and on the profile with an audit entry.

**Acceptance Scenarios**:

1. **Given** a current academic year exists, **When** the admin fills all required fields and saves, **Then** the student is created as Active with entry source Manual, a serial number is assigned, a class record for the current year is created, and the admin sees a success message. (FR-001, FR-006)
2. **Given** a GR No. already used by any student (including one who left), **When** the admin saves, **Then** the save is refused with a message next to the GR No. field. (FR-002)
3. **Given** another student has the same first, middle and surname, **When** the admin saves, **Then** a warning names the matching student(s) and the admin can still save. (FR-003)
4. **Given** the admin uses "Save and add next", **When** the save succeeds, **Then** the form is cleared, the cursor is in the GR No. field, and a message shows the GR No. just saved. (FR-011)
5. **Given** the registration screen is open, **When** the admin uses only Tab, Shift+Tab, the arrow keys and Enter, **Then** every field and both save buttons can be reached and used, in the order they appear on the screen. (FR-011)
6. **Given** a required field is empty or invalid (for example Mobile 1, or an admission date after today), **When** the admin leaves the field or saves, **Then** the problem is shown next to the field and nothing is saved. (FR-017)
7. **Given** a photo is chosen, **When** the student is saved, **Then** the photo is stored resized; **Given** no photo, **Then** the student's initials are shown instead. (FR-005)

---

### User Story 2 - Find students and view a profile (Priority: P1)

The admin opens the Students list, searches or filters, and presses View profile to see everything about one student on one page: photo, personal details, contact and admission, and class history.

**Why this priority**: Registering is only useful if students can be found and checked again (STU-04).

**Independent Test**: With several students saved, search by name, GR No. and mobile; filter by standard, division and category; open a profile and compare it with the saved details.

**Acceptance Scenarios**:

1. **Given** students exist, **When** the admin opens Students, **Then** the list shows Active students only, with photo or initials, GR No., name, Std / Div, category and a View profile button, one page at a time with the total count, and a Register student button. (FR-008)
2. **Given** the list, **When** the admin searches and applies column filters, **Then** only rows matching both are shown; Export to Excel contains all matching rows, not only the visible page. (FR-008)
3. **Given** a student with Aadhaar and religion/caste saved, **When** the admin views the list, an export or the profile, **Then** those values are masked (last 4 characters visible) and cannot be searched or filtered by. (FR-010)
4. **Given** a student's profile, **When** it opens, **Then** it shows photo or initials, name, status, GR No., standard and division, category and academic year, personal details, contact and admission, and class history (year, Std / Div, category, year status), with no roll number and no fee or receipt information. (FR-007, FR-009)
5. **Given** a student with no class record for the current academic year but one for an earlier year, **When** the list or the profile heading shows Std / Div and category, **Then** it shows the most recent record's values with that record's academic year in small text beside them. (FR-008)
6. **Given** a student whose details, class record or status were changed, **When** the admin opens the profile, **Then** a History block lists those changes, newest first, each with date and time, action, what changed (old and new values, Aadhaar and religion/caste masked); **Given** a student with no changes after registration, **Then** the block shows only the registration entry. (FR-019)

---

### User Story 3 - Edit a student and the yearly class record (Priority: P2)

The admin corrects a student's details, changes the class (standard, division, category) for a year, and marks a student as Left.

**Why this priority**: Mistakes happen during bulk entry, and classes change during the year. Less urgent than creating and finding students.

**Independent Test**: Edit a student's mobile number and class record, mark the student Left, and check the profile, list and audit log.

**Acceptance Scenarios**:

1. **Given** a student, **When** the admin edits details and saves, **Then** the changes show on the profile and an audit entry records the old and new values (sensitive fields masked). (FR-004, FR-012)
2. **Given** a student with a class record for the current year, **When** the admin changes the standard, division or category, **Then** that year's row changes and the profile's class history shows it; there is never more than one row per student per academic year. (FR-006)
3. **Given** a student or class record that was changed in another window, **When** the admin saves a stale edit, **Then** the save is refused, the admin is told the record has changed, and the latest values are shown. (FR-018)
4. **Given** an Active student's profile, **When** the admin presses Mark as left and confirms, **Then** the status changes to Left, an audit entry is written, and the student is never deleted. No date or reason is asked (F5 adds those with the leaving certificate). The student no longer appears in the default Students list but does when the Status filter is Left or All, and there is no way to set the status back to Active. (FR-004, FR-009)
5. **Given** a student who left and later returns, **When** the admin registers them again, **Then** they get a new GR No. and a new student record; the old record is kept as it was. (FR-002)

---

### User Story 4 - New application shell (Priority: P2)

All screens, including the existing sign-in, master data and audit log screens, appear inside a new shell that matches the prototype: a top bar with tabs, a second row of pills under Finance, a left list under Setup, and content centred at most 1280 px wide, using the current purple colours.

**Why this priority**: The Students screens are designed for this shell, and the F0 sidebar is replaced as decided on 2026-10-25. It can be tested without any student data.

**Independent Test**: Sign in and move through every visible tab and menu item; check that F0 screens still work and that unbuilt tabs are not shown.

**Acceptance Scenarios**:

1. **Given** the admin is signed in, **When** any screen opens, **Then** the top bar shows only tabs whose feature is built (in this feature: Students and Setup), the current academic year, the signed-in name, Change password and Logout. (FR-013)
2. **Given** the Setup tab, **When** the admin opens it, **Then** a left list holds the F0 pages (Master data: Academic years, Standards, Divisions, Student categories; System: Audit log) and each works as before. (FR-014)
3. **Given** a tab or menu item whose feature is not built (Dashboard, Approvals, Staff, Finance, Reports, Excel import), **When** the admin looks at the top bar and the Setup list, **Then** it is not shown. (FR-013)
4. **Given** no academic year covers today, **When** the admin signs in and tries to open any other screen, **Then** only Change password, Logout and the Academic years create form can be reached, as in F0. (FR-014)
5. **Given** a wide screen, **When** any page opens, **Then** its content is centred and no wider than 1280 px; **Given** tablet or phone width, **When** a table is wider than the screen, **Then** it scrolls sideways inside its card. (FR-013)

---

### Edge Cases

- No current academic year: Register student and the Students screens are blocked by the F0 rule; the admin is sent to create a year first (FR-014).
- No divisions exist for the current year and the chosen standard: the admin cannot pick a division and is told to add one under Setup (FR-001, FR-006).
- A standard, division or category marked inactive (F0): hidden from choices for new records, still shown on existing records and history (FR-006).
- A GR No. typed with different capital letters or spaces around it: treated as the same GR No. (FR-002, see Assumptions).
- Photo that is not a JPEG or PNG image, or is larger than 5 MB: refused with a message beside the photo; nothing is saved until the admin removes the photo or chooses another (FR-005).
- Mobile 1 missing or not a valid number: refused; Mobile 2 is optional but validated if given (FR-017).
- Search text matching Aadhaar or religion/caste values: those columns are never searched, so no match is returned from them (FR-010).
- Admission date after today, or before the date of birth: refused (FR-017).
- A student with the same name as a Left student: the duplicate-name warning also names Left students (FR-003).
- A student with no class record for the current year (for example a student registered in an earlier year): the list shows the most recent record with its year (FR-008).
- Two windows edit the same student or class record: the second save is refused (FR-018).

## Requirements *(mandatory)*

Each requirement names the user story and the agreement or decision it comes from.

### Functional Requirements

**Students**

- **FR-001** *(US1; STU-01, agreement 4.3)*: Admin MUST be able to register a student with the agreement's fields: GR No. (typed, required), surname, first name, middle name (optional), gender, date of birth, place of birth (optional), father's and mother's names (optional), Mobile 1 (required), Mobile 2 (optional), address, Aadhaar (optional), religion/caste (optional), category, admission date, admission standard, previous school (optional) and photo (optional), together with the standard and division for the current academic year. The system MUST assign a serial number that is never reused. New students are Active with entry source Manual.
- **FR-002** *(US1, US3; STU-01, decision log 2026-10-25)*: System MUST refuse a GR No. that any student, Active or Left, already has or ever had, and a GR No. MUST NOT be reused, including one replaced by a correction (FR-004). A student who leaves and returns MUST be registered again with a new GR No.
- **FR-003** *(US1; STU-01, decision log 2026-10-25)*: System MUST warn, without blocking the save, when another student (Active or Left) has the same first name, middle name and surname, naming the matching students.
- **FR-004** *(US3; STU-01, STU-04)*: Admin MUST be able to edit a student's details, including correcting the GR No. (audited with old and new value; the old number stays reserved), and mark an Active student as Left. Marking Left is a plain status change with no leaving date or reason (F5 adds those with the leaving certificate), and a Left student MUST NOT be set back to Active; a returning student is registered again (FR-002). Students MUST NOT be deleted.
- **FR-005** *(US1; STU-01, agreement 4.3 Photo)*: Admin MUST be able to add, replace and remove a photo. System MUST accept only JPEG or PNG files up to 5 MB, refuse others with a message beside the photo, and resize the photo when it is uploaded. Where there is no photo, System MUST show the student's initials (list and profile).
- **FR-017** *(US1; STU-01)*: System MUST refuse a registration or edit and show the problem beside the field when: a required field is empty; Mobile 1 or Mobile 2 (if given) is not a 10-digit number; Aadhaar (if given) is not 12 digits (spaces ignored); the admission date is after today (school's local date) or before the date of birth; or the division does not belong to the chosen standard in the current academic year.

**Yearly class record**

- **FR-006** *(US1, US3; STU-03)*: System MUST keep one class record per student per academic year with standard, division and category for that year. Registering a student MUST create the record for the current academic year; the admin MUST be able to change a record's standard, division and category. Registration asks for one category, which is stored on the student and copied to the first class record; a later change to a year's category changes only that year's record, and the yearly category is the one fee features use. The change form offers standard, division and category for that record's year; the division must belong to that year and standard; inactive standards, divisions and categories are not offered, but an existing record keeps them. It follows the same rules as FR-017 and FR-018, with no new screen or flow.
- **FR-007** *(US2; STU-03, STU-04)*: The profile MUST show the class history, one row per year, with year, Std / Div, category and year status. Roll number is not stored or shown anywhere. In this feature every new class record has year status Studying and the admin cannot edit it; marking a student Left does not change it. Promoted and Detained are set by F4, and Left by F5.
- **FR-018** *(US3; decision log 2026-10-25, F0 FR-046)*: When the admin saves an edit of a student, an edit of a class record, or Mark as left on a record that was changed after the screen was opened, System MUST refuse the save, tell the admin the record has changed, and show the latest values so the edit can be repeated (a stale-version conflict, as in F0).

**List and profile**

- **FR-008** *(US2; STU-04, prototype Students)*: The Students list MUST follow the standard table rules from F0 (paging, search, column filters, sort, Export to Excel of all matching rows) and show photo or initials, GR No., name, Std / Div, category, a View profile button, and a Register student button. Search MUST match name, GR No. and Mobile 1 or 2. By default the list shows Active students only; a Status filter (Active, Left, All) shows Left students. The Std / Div and Category columns show the current academic year's class record; when the student has none for the current year, they show the most recent record, with that record's academic year in small text beside the value. Filters and sorting on Std / Div and Category apply to the record shown.
- **FR-009** *(US2, US3; STU-04, prototype Student profile)*: The Student profile MUST show photo or initials, name, status, personal details, contact and admission (including entry source; Mobile 1 and Mobile 2 shown in full), the class history with a way to change each year's class record (FR-006), the history of changes (FR-019), and a link back to the list. The category shown in the heading, the class history and the list is the yearly one; the category chosen at registration is not shown again and the edit form does not ask for it. It MUST offer Edit details and, for an Active student only, a plain Mark as left button that asks for confirmation and changes the status only (FR-004). It MUST NOT show fee columns, receipts, Collect fee, a prepare-LC button or a roll number; those come with later features.
- **FR-010** *(US2; agreement 4.3 sensitive fields, constitution VI)*: Aadhaar and religion/caste MUST be shown masked (last 4 characters visible) on the list, the profile (which shows both) and in Excel exports, and MUST NOT be searchable or filterable. The full value is shown only in the edit form, so the admin can correct it. Both are stored as plain text; no field-level encryption is required.

- **FR-019** *(US2; STU-04, agreement audit log, constitution III)*: The profile MUST show a History block with the audit entries of that student's data: the student's own entries and those of the student's class records, newest first, each with date and time, user, action and the old and new values, with Aadhaar and religion/caste masked. A student with no entries other than registration shows that entry only. The block is read-only.

**Entry speed**

- **FR-011** *(US1; decision log, existing students entry volume)*: The registration screen MUST offer "Save and add next", which clears the form, puts the cursor in the GR No. field and shows a message with the GR No. just saved. Every field and both save actions MUST be reachable and usable with the keyboard only (Tab, Shift+Tab, the arrow keys and Enter), in the order they appear on the screen. Field problems MUST be shown next to the field.

**Audit**

- **FR-012** *(US3; constitution III, F0 FR-023, FR-025)*: Every student create, edit and status change, every photo add, replace or removal (without the image), and every class record create and change, MUST write an audit entry (area Students, old and new values, sensitive fields masked) in the same transaction as the change. If the entry cannot be saved, the change MUST NOT be saved. Excel exports of the list MUST be recorded as in F0.

**Shell**

- **FR-013** *(US4; decision log 2026-10-25, ui-prototype)*: The app shell MUST match `docs/design/ui-prototype.md`: a top bar with tabs (Dashboard, Approvals, Students, Staff, Finance, Reports, Setup), a second row of pills under Finance, a left list under Setup, and content centred at most 1280 px wide, using the current purple colour tokens, not the prototype's hex values. Only tabs, pills and menu items whose feature is built MUST be shown.
- **FR-014** *(US4; F0 FR-009, FR-013, FR-028)*: The existing F0 screens (sign in, Change password, master data pages, Audit log) MUST keep working inside the new shell, including the no-current-year block and the sign-in password popup.
- **FR-015** *(US2, US4; decision log 2026-10-25, ui-prototype)*: The Students list and Student profile MUST match the prototype images (layout and content) except for the following, which stay hidden until their features exist: the fee columns and Fees filter, the fees and receipts blocks, Collect fee, the prepare-LC part of "Mark as left and prepare LC" (the profile has the plain Mark as left of FR-009), Promote a class, Left students (LC), the Approvals badge, the global search box and roll number.

**Testing**

- **FR-016** *(all stories; constitution VIII)*: Every requirement in this specification MUST have at least one automated test that references its ID.

### Key Entities

- **Student**: The permanent record of a person: GR No., serial number, names, gender, date of birth, parents, contact numbers, address, sensitive fields (Aadhaar, religion/caste), category, admission date and standard, previous school, photo, status (Active / Left) and entry source (Manual / Google Form). Never deleted.
- **Yearly Class Record**: One row per student per academic year: standard, division, category for that year and year status. Links the student to F0 master data.
- **Audit Entry**: Reused from F0, with the new area Students.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** *(FR-001, FR-011, FR-017)*: An admin who has used the form once can register a student with all required fields in under 2 minutes, and can enter 20 students in a row without the mouse.
- **SC-002** *(FR-002, FR-003)*: 100% of registrations with an already-used GR No. are refused, and 100% of registrations with a matching name show the warning and can still be saved.
- **SC-003** *(FR-008)*: On a list of 10,000 students (the same size as F0's list tests), a search, filter, sort or page change shows results in under 2 seconds on the target school computer (Windows 10, 4 GB RAM, decision log 2026-10-25), and an Excel export of 10,000 students completes in under 30 seconds.
- **SC-004** *(FR-010, FR-012)*: In 100% of tested cases Aadhaar and religion/caste appear only masked on the list, profile, export and audit log, and searching for one of their values returns no row.
- **SC-005** *(FR-004, FR-006, FR-012)*: 100% of student creates, edits, status changes and class record changes have a matching audit entry, and no entry exists for a change that failed.
- **SC-006** *(FR-008, FR-009)*: The admin can find a student by name, GR No. or mobile and open the profile in under 30 seconds.
- **SC-007** *(FR-013, FR-014)*: After the shell change, every F0 screen can still be reached and used, and no tab or menu item for an unbuilt feature is visible.
- **SC-008** *(FR-016)*: Every functional requirement is covered by at least one passing test.
- **SC-010** *(FR-019)*: For a student that was edited, the profile's History block lists every change with its old and new values, and shows no full Aadhaar or religion/caste value, in 100% of tested cases.
- **SC-009** *(FR-018)*: In 100% of tested cases a stale save of a student or class record is refused and nothing is overwritten.

## Assumptions

- One admin, one school computer; the F0 rules for sign-in, current academic year, paging, audit and Excel export apply unchanged.
- The current academic year is the one worked out in F0. Registration needs it, because the first class record is created for it.
- Registering a student asks for the current year's standard and division, and one category (see FR-006 and Clarifications).
- GR No. is free text, compared ignoring capital letters and spaces at both ends; its format is the school's own and is not validated.
- Mobile numbers are valid when they hold 10 digits (optional +91 or leading 0 allowed). Gender choices are Male, Female and Other. These are not in the agreement and are easy to change.
- Photos accept JPEG or PNG, up to 5 MB before resizing, and are resized so the longest side is about 400 pixels.
- Names are compared for the duplicate warning ignoring capital letters and spaces at both ends.
- A student's class record for a past year may be changed by the admin; there is no year-end lock in this feature. F3 will decide whether fee records lock a year's class record.
- The student's standard, division and category for a year come from F0 master data (standards, divisions of that year, categories).
- Entry source Google Form is a value that exists but is set only by F1b; in this feature every student is Manual, and the admin cannot choose it.
- Out of scope: Google Form import and Approvals (F1b), fee columns and receipts on the profile (F3), the leaving certificate and Left Students Register (F5), promotion (F4), Excel import, staff photos, roll numbers.

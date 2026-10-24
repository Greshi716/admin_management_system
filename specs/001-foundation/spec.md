# Feature Specification: Foundation (F0)

**Feature Branch**: `001-foundation`

**Created**: 2026-10-24

**Status**: Draft

**Input**: User description: "Feature F0: Foundation for CampusFlow. Source of truth: docs/agreement/CampusFlow_Agreement_v1.1_2026-10-24.md (sections 2, 3 ADM-01 and ADM-04, 4.2, 4.8, 7). In scope: single admin login using the users table (hashed password, default Admin created at installation, no role-based access control); master data screens for academic years (only one current), standards, divisions, and student categories with the affects_fees flag (seed: O/S, CEW, W, STAFF, S STAFF, RTE, CASUAL); audit log of changes and logins, filterable by date; app shell with navigation, layout, logout; a reusable server-side paged table with search, column filters, sorting and Excel export. Out of scope: fee amounts, students, staff, payroll, backups. Anything not in the agreement must be marked NEEDS-CLARIFICATION."

## Clarifications

### Session 2026-10-24

- Q: After the recovery code is used, does it stay valid? → A: No. The used code stops working, a new code is created and shown once, and the reset is audited.
- Q: What happens when a save conflicts with a change made in another window? → A: The save is refused, the admin is told the record changed, and the latest values are shown so they can re-apply their edit.
- Q: Can two academic years have overlapping dates? → A: No. A year whose dates overlap any other year is refused with a clear message.
- Q: Does the audit log keep values of sensitive fields (such as Aadhaar or bank account)? → A: Only masked values (last 4 characters shown, the rest hidden). Passwords, hashes and recovery codes are never stored in any form.
- Q: Does an Excel export include hidden columns or only the columns shown on screen? → A: All columns the list can show, regardless of what is hidden on screen; sensitive columns stay masked as on screen.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Admin signs in and out (Priority: P1)

The school admin opens CampusFlow on the school computer, signs in with a username and password, uses the application, and signs out. Nobody else can see any school data without signing in. On a fresh installation a default Admin account already exists, with a random password shown once at installation, so the school can sign in for the first time.

**Why this priority**: Every other module sits behind the login. Nothing can be shown or changed safely without it.

**Independent Test**: On a fresh installation, sign in with the default Admin account, see the app shell, sign out, and confirm that opening any screen again asks for sign-in.

**Acceptance Scenarios**:

1. **Given** a fresh installation, **When** the admin signs in with the default Admin account, **Then** the app shell opens and the last login time of that user is updated.
2. **Given** a wrong username or password, **When** the admin tries to sign in, **Then** access is refused with a message that does not say which of the two was wrong, and the failed attempt is recorded.
3. **Given** a signed-in admin, **When** they choose Logout, **Then** the session ends and every screen asks for sign-in again, including after pressing the browser's Back button.
4. **Given** a user who is not signed in, **When** they open any screen address directly, **Then** they are taken to the sign-in page.
5. **Given** the user record is marked not active, **When** that user tries to sign in, **Then** access is refused.
6. **Given** 5 failed sign-ins in a row, **When** the admin tries again, **Then** sign-in is paused for 5 minutes with a message saying when to try again, and the pause is recorded.
7. **Given** the admin has forgotten the password, **When** they enter the recovery code created at installation, **Then** they can set a new password, the used code stops working, and a new recovery code is shown once.
8. **Given** a signed-in admin with no activity for 10 minutes, **When** they take the next action, **Then** they are signed out and taken to the sign-in page.
9. **Given** the default password has not been changed, **When** the admin is signed in, **Then** a banner reminds them to change it and stays until they do. They can change it at any time from the app shell, and no change is forced.

---

### User Story 2 - Manage academic years, standards and divisions (Priority: P1)

The admin sets up the school's structure: academic years (for example 2026-27, 01-06-2026 to 31-05-2027), standards (1 to 12, with Science or Commerce stream for 11 and 12, and a display order), and divisions (A, B and so on) for each standard in each academic year. Exactly one academic year is the current one.

**Why this priority**: Students, fees and payroll all hang off the current academic year and the standard and division structure.

**Independent Test**: Create an academic year, make it current, add standards, add divisions for a standard in that year, and confirm lists show them in the right order and only one year is current.

**Acceptance Scenarios**:

1. **Given** no academic years, **When** the admin adds "2026-27" with start and end dates, **Then** it appears in the academic year list.
2. **Given** year A is current, **When** the admin makes year B current, **Then** B is current and A is no longer current, in one step, so there are never zero or two current years afterward.
3. **Given** an academic year whose end date is not after its start date, **When** the admin saves it, **Then** the save is refused with a clear message.
4. **Given** a standard named "11" with stream Science, **When** the admin adds it, **Then** it is listed in display order and the stream is shown.
5. **Given** a division "A" already exists for standard 5 in 2026-27, **When** the admin adds another "A" for the same standard and year, **Then** the save is refused as a duplicate; the same "A" in a different year or standard is allowed.
6. **Given** the add-division form, **When** it opens, **Then** only existing years and standards can be chosen.
7. **Given** an academic year already covers 01-06-2026 to 31-05-2027, **When** the admin saves another year with dates inside or crossing that range, **Then** the save is refused with a message naming the overlapping year.

---

### User Story 3 - Manage student categories (Priority: P2)

The admin maintains the school's student categories. Each has a code, a name and an "affects fees" flag (yes means a separate fee amount applies to this category). The installation comes with the seven categories from the class formation sheet: O/S, CEW, W, STAFF, S STAFF, RTE, CASUAL.

**Why this priority**: Needed before fee structure and student entry, but nothing else in F0 depends on it.

**Independent Test**: On a fresh installation open Student Categories, see the seven seeded categories, add a new one with the flag set, change the flag on another, and confirm the list reflects both.

**Acceptance Scenarios**:

1. **Given** a fresh installation, **When** the admin opens Student Categories, **Then** O/S, CEW, W, STAFF, S STAFF, RTE and CASUAL are listed.
2. **Given** a new category with code, name and the affects-fees flag chosen, **When** the admin saves it, **Then** it is listed with that flag.
3. **Given** a category code that already exists, **When** the admin adds it again, **Then** the save is refused as a duplicate.
4. **Given** a category without the affects-fees flag chosen, **When** the admin saves, **Then** the save is refused because the flag is required.

---

### User Story 4 - Review the audit log (Priority: P2)

Every change made in the system, and every sign-in and sign-out, is recorded automatically with date and time, who did it, what kind of action, which area of the system (Security, Master data, and later Fees, Payroll and Fund), which record, and the old and new values. The admin can open the audit log, filter it by date (date range, month or year), and see what happened. The log cannot be edited or deleted from the application.

**Why this priority**: The constitution and the agreement require every financial change to be audited, so the mechanism must exist before any financial module is built. The screen is how the admin sees that it works.

**Independent Test**: Sign in, edit a standard, then open the audit log and find the login entry and the edit entry with old and new values; filter by today's date and by a past date range.

**Acceptance Scenarios**:

1. **Given** the admin edits a master data record, **When** they open the audit log, **Then** there is an Edit entry with time, user, record type and ID, and the old and new values.
2. **Given** a successful or failed sign-in, **When** the log is opened, **Then** a Login entry exists for it.
3. **Given** entries on several dates, **When** the admin filters by a date range, a month, or a year, **Then** only entries inside that period are shown.
4. **Given** the audit log screen, **When** the admin looks for a way to edit or delete an entry, **Then** none exists.
5. **Given** a field marked sensitive in the agreement (for example an Aadhaar number), **When** it changes, **Then** the log shows only the masked old and new values (last 4 characters visible). **Given** a password, password hash or recovery code changes, **Then** the log records that it changed but never any part of the value.
6. **Given** a change that fails and is rolled back, **When** the log is opened, **Then** no entry claims the change happened.
7. **Given** entries from different areas (Security, Master data, and later Fees, Payroll and Fund), **When** the admin filters by Area or by Action, **Then** only matching entries are shown. There is one audit log, not several.

---

### User Story 5 - Work with lists using the standard table (Priority: P2)

Every list in CampusFlow (the master data lists and the audit log in this feature, and all later lists) uses one common table. It loads a page at a time from the server, has a search box, a filter on any column, sortable columns, and an Export to Excel button that exports what the admin is currently looking at.

**Why this priority**: Section 7 of the agreement says every list screen has these abilities. Building it once avoids repeating it per module, but the lists stay usable at small sizes without it.

**Independent Test**: Load a list with more rows than one page, move between pages, search, filter on a column, sort a column, and export; the Excel file matches the filtered and sorted rows.

**Acceptance Scenarios**:

1. **Given** a list with more rows than fit on one page, **When** the admin moves to the next page, **Then** only that page's rows are loaded and shown, with the total count visible.
2. **Given** text in the search box, **When** it is applied, **Then** only matching rows are shown and the total count updates.
3. **Given** the admin types in the search box, **When** they pause typing briefly, **Then** the results update without pressing Enter.
4. **Given** filters on one or more columns, **When** applied together with a search, **Then** rows must satisfy all of them.
5. **Given** a column heading, **When** the admin selects it, **Then** rows sort ascending, then descending on the next selection, across all pages and not only the visible page.
6. **Given** an active search, filters and sort, **When** the admin chooses Export to Excel, **Then** the file contains all matching rows (not only the current page) in the current order, with every column the list can show (including any hidden on screen) and the column headings.
7. **Given** a result with no matching rows, **When** displayed, **Then** the table shows an empty-state message and Export produces a file with headings only or is clearly disabled.
8. **Given** an export, **When** it completes, **Then** an Export entry appears in the audit log.

---

### User Story 6 - Navigate the app shell (Priority: P3)

After signing in the admin sees a consistent layout: a navigation menu for the available areas (Master Data, Audit Log, and later modules as they are added), the name of the signed-in user, the current academic year, and a Logout control.

**Why this priority**: It frames every screen, but the screens can be reached without polish.

**Independent Test**: Sign in and confirm that every menu entry opens its screen, the current academic year and user name are visible on every screen, and Logout works from every screen.

**Acceptance Scenarios**:

1. **Given** a signed-in admin, **When** any screen is open, **Then** the navigation, the user's name, the current academic year, Change password and Logout are visible.
2. **Given** no academic year is current, **When** the shell loads, **Then** it says that no current year is set and links to the academic year screen.
3. **Given** a module that is not yet built, **When** the admin looks at the menu, **Then** it is either absent or clearly marked as unavailable.

---

### Edge Cases

- Two browser windows open: the admin makes a year current in one and edits it in the other; the second save is refused, the admin is told the record changed, and the latest values are shown (FR-046); it never leaves two current years.
- Idle session: signed out after 10 minutes without activity (FR-008).
- Removing master data already in use (a standard, division, year or category referenced by later records): it cannot be deleted; it can be marked inactive instead (FR-020).
- Default Admin credentials: a random password is shown once at installation; the admin may change it but is not forced to (FR-009).
- Many failed sign-ins in a row: after 5 in a row sign-in is paused for 5 minutes (FR-044); a forgotten password is recovered with the recovery code (FR-045).
- Export of a very large result: the export must still contain every matching row and must not freeze the application for other screens.
- Search or filter text containing special characters or Gujarati text is handled as plain text and still matches.
- A date filter whose "from" is after "to": the table shows a message and no results rather than failing.

## Requirements *(mandatory)*

### Functional Requirements

**Sign-in (ADM-04, agreement section 2)**

- **FR-001**: System MUST require sign-in before showing any screen or data, using a username and password checked against the `users` records.
- **FR-002**: System MUST store each password only as a hash and MUST NOT show, log or audit the password or its hash.
- **FR-003**: System MUST create a default user with the role Admin at installation, as the only role; there is no role-based access control and no screen to create other roles.
- **FR-004**: System MUST refuse sign-in for wrong credentials or for a user whose Active flag is off, without revealing which part was wrong.
- **FR-005**: System MUST update the user's last login time on each successful sign-in.
- **FR-006**: System MUST end the session on Logout so that no screen can be reached without signing in again.
- **FR-007**: System MUST redirect any unauthenticated request for a screen to the sign-in page.
- **FR-008**: System MUST sign out an admin after 10 minutes without activity, and the sign-out MUST be recorded.
- **FR-009**: System MUST create the default Admin with a random password generated at installation and shown once. The admin MUST be able to change the password at any time. Until it is changed a banner reminds them, and no change is forced.
- **FR-044**: After 5 consecutive failed sign-ins, System MUST pause sign-in for 5 minutes, tell the admin when they can try again, and record the pause.
- **FR-045**: System MUST create a recovery code at installation, show it once, and store only its hash. A forgotten password MUST be reset by entering this code. A recovery code works only once: after a successful reset it MUST stop working, and System MUST create a new code, show it once, and store only its hash. The reset is recorded as a Security audit entry that never shows the code. Email-based reset is out of scope.

**Academic years (ADM-01, `academic_years`)**

- **FR-010**: Admin MUST be able to create, view and edit academic years with name, start date and end date, all required; the name MUST be unique.
- **FR-011**: System MUST reject an academic year whose end date is not after its start date, or whose dates overlap those of any other academic year, with a clear message.
- **FR-012**: System MUST ensure exactly one academic year is current at any time once at least one exists; making a year current MUST remove the flag from the previous one in the same operation.
- **FR-013**: The current flag MUST be set by the system when the admin chooses "make current", not typed as a free field.

**Standards (`standards`)**

- **FR-014**: Admin MUST be able to create, view and edit standards with name and display order (required) and stream (optional, Science or Commerce).
- **FR-015**: Standard names MUST be unique; standards MUST be listed in display order by default.

**Divisions (`divisions`)**

- **FR-016**: Admin MUST be able to create, view and edit divisions, each linked to an existing academic year and an existing standard, with a division name.
- **FR-017**: System MUST refuse a second division with the same name for the same standard and academic year.

**Student categories (`student_categories`)**

- **FR-018**: System MUST come with the categories O/S, CEW, W, STAFF, S STAFF, RTE and CASUAL after installation.
- **FR-019**: Admin MUST be able to create, view and edit categories with code, name and the affects-fees flag, all required; the code MUST be unique.

**Removal of master data**

- **FR-020**: A master data record (academic year, standard, division or category) that is referenced by other records MUST NOT be deleted. The admin can mark it inactive, which hides it from choices for new records and keeps it on existing records and in history. A record that is not referenced MAY be deleted. The current academic year cannot be marked inactive. There is no purge. Deletes and inactive changes are audited.

- **FR-046**: When the admin saves an edit to a record that was changed after the form was opened, System MUST refuse the save, tell the admin the record has changed, and show the latest values so they can re-apply their edit. This applies to every editable record in F0.

**Audit log (FND-05 foundation, `audit_log`)**

- **FR-021**: System MUST automatically record, for every create, edit, delete and inactive change of a master data record, the date and time, the user, the action, the area (Master data), the record type and ID, and the old and new values.
- **FR-022**: System MUST record every sign-in attempt (successful and failed), every sign-out (including automatic idle sign-out), every sign-in pause and every Excel export, with the Login or Export action. A password change is recorded as an Edit that never shows the password or its hash. Sign-in, sign-out, pause and password entries belong to the Security area.
- **FR-023**: An audit entry MUST be saved in the same operation as the change it describes; if the change fails, no entry is left, and if the entry cannot be saved the change MUST NOT be saved.
- **FR-024**: System MUST provide no way, from the application, to edit or delete an audit entry.
- **FR-025**: Audit entries MUST NOT contain the password, password hash or recovery code in any form; they record only that it changed. For any other field marked sensitive in the agreement, entries MUST contain only a masked value with the last 4 characters visible and the rest hidden, never the full value.
- **FR-026**: Admin MUST be able to view the audit log and filter it by date range, month, year, area and action, in addition to the standard table abilities (FR-029 to FR-034).
- **FR-027**: The audit log MUST be usable by later modules to record their own changes under their own area (Fees, Payroll, Fund), including the Cancel, Finalise and Import actions and a reason, without changing its structure.

**App shell**

- **FR-028**: After sign-in, every screen MUST show the navigation menu, the signed-in user's name, the current academic year (or a notice that none is set), a Change password control and a Logout control.

**Standard table**

- **FR-029**: Every list MUST load from the server one page at a time and show the total number of matching rows; the page size MUST be adjustable among a small set of choices.
- **FR-030**: Every list MUST have a search box that matches across the list's visible text columns.
- **FR-031**: Every list MUST allow filtering on any column, and search and column filters MUST combine so that a row must satisfy all.
- **FR-032**: Every list MUST allow sorting by any column, applied across the whole result and not only the visible page.
- **FR-033**: Every list MUST offer Export to Excel that includes all rows matching the current search, filters and sort, with column headings, not only the current page. The file MUST include every column the list can show, including columns hidden on screen; sensitive columns MUST stay masked as on screen.
- **FR-034**: The standard table MUST be reusable: a new list gets all of FR-029 to FR-033 by declaring its columns, without re-implementing paging, search, filter, sort or export.
- **FR-035**: Lists MUST stay responsive with many thousands of rows, because only one page is loaded at a time.

**Cross-cutting**

- **FR-036**: Every requirement in this specification MUST have at least one automated test that references its ID (constitution Principle VIII).

**Look and feel**

- **FR-037**: Moving between screens MUST NOT reload the whole page.
- **FR-038**: Every list and form MUST show a loading state while data loads, never a blank screen.
- **FR-039**: Saving MUST show a clear success or error message.
- **FR-040**: Forms MUST validate fields as the admin types and show what to fix next to the field.
- **FR-041**: Screens MUST work at desktop and tablet sizes.
- **FR-042**: Gujarati text MUST display correctly everywhere it appears.
- **FR-043**: All screens MUST share one consistent visual style.

### Key Entities

- **User**: The person who signs in. Full name, unique username, hashed password, role (Admin only), active flag, last login time, a hashed recovery code, and sign-in failure tracking for the 5-minute pause.
- **Academic Year**: A school year with name, start date, end date and a current flag; only one is current.
- **Standard**: A class taught in the school, with name, display order and optional stream (Science or Commerce).
- **Division**: A division (A, B and so on) of one standard in one academic year.
- **Student Category**: A fee-relevant grouping with code, name and an affects-fees flag; seven are supplied at installation.
- **Audit Entry**: An unchangeable record of one action: date and time, user, area, action (Create, Edit, Delete, Login, Export now; Cancel, Finalise and Import reserved for later modules), record type and ID, old and new values, and an optional reason.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a fresh installation, the admin can reach the signed-in home screen within 1 minute using only the default Admin account.
- **SC-002**: 100% of screens and data are unreachable without signing in, verified by opening every screen address while signed out.
- **SC-003**: After any attempt to change which year is current, there is exactly one current year, in 100% of tested cases including two overlapping changes.
- **SC-004**: 100% of master data creates, edits and deletes, and of sign-ins, sign-outs and exports, have a matching audit entry, and 0 audit entries exist for changes that failed.
- **SC-005**: The admin can find a specific audit entry from a given day within 30 seconds using the date filter.
- **SC-006**: On a list of 10,000 rows, a page, search, filter or sort result appears in under 2 seconds on the school's normal computer.
- **SC-007**: An Excel export of 10,000 matching rows completes in under 30 seconds and contains exactly the rows and order given by the current search, filters and sort, with every column the list can show.
- **SC-008**: The admin can add a new academic year, its divisions and a new student category in under 10 minutes without help.
- **SC-009**: Every functional requirement is covered by at least one passing test.
- **SC-010**: After 5 failed sign-ins in a row, a sixth attempt within 5 minutes is refused in 100% of tested cases.
- **SC-011**: An admin with no activity for 10 minutes is signed out in 100% of tested cases.

## Assumptions

- One school, one admin user, one computer; other computers use a browser on the school network (agreement sections 2 and 10).
- The agreement's "Current year" is a system-set field; the admin makes a year current with an explicit action.
- Failed sign-in attempts are recorded in the audit log. The lock-out (5 failures, 5 minutes), the 10-minute idle sign-out and the random default password were decided at spec review and are not in the agreement.
- The default page size is a normal value such as 25 rows, chosen from a small set of options.
- Screens are in English; Gujarati wording of notices is defined in later feature specs (agreement section 10).
- The audit actions are those in the agreement plus Delete, and each audit entry has an Area. Both were added to the agreement at spec review. Cancel, Finalise and Import are only reserved here.
- Fee amounts, students, staff, payroll, backups, receipts and the fee structure are out of scope for F0 and appear in later features.
- The master data screens in this feature hold no sensitive fields; the sensitive-field masking rule (FR-025) is a foundation for later features, and F0 applies it to the password, hash and recovery code. Masked values in the audit log differ from the agreement's "kept out of logs" wording; the agreement and its decision log need a matching update.
- The audit log has no retention limit or purge in version 1, since the agreement defines none and the constitution forbids removing financial history.
- Email-based password reset needs email settings and internet access. It is out of scope for version 1 (NEEDS-CLARIFICATION for a later feature).

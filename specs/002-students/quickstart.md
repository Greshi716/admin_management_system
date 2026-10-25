# Quickstart: Validating Students and Class Records (F1)

Run-and-check guide. Prerequisites, JDK 21, Node and the two databases are as in
[../001-foundation/quickstart.md](../001-foundation/quickstart.md). Contracts:
[contracts/openapi.yaml](contracts/openapi.yaml); tables: [data-model.md](data-model.md). Use only synthetic
students (constitution VI).

## 1. Automatic checks

```powershell
scripts\verify.ps1      # backend tests, frontend build and unit tests, Playwright, FR coverage (port 8080 free)
```

Expected: all pass, and the FR coverage check reports FR-001 to FR-019 (F1) and FR-001 to FR-046 (F0) covered.
Migration check: Flyway applies `V6__students.sql` on an empty and on an F0 database.

## 2. Setup for the manual scenarios

Sign in. Make sure an academic year covers today and that one standard with at least two divisions and the
seven categories exist (Setup, Master data). Then open Students.

## 3. Scenarios

| # | Do | Expect | FR |
|---|---|---|---|
| 1 | Register a student with all required fields and no photo | Saved as Active, source Manual, serial number shown on the profile, a class record for the current year, initials shown, success message | 001, 005, 006 |
| 2 | Register again with the same GR No. in other capitals and with spaces around it | Refused, message next to GR No.; nothing saved | 002 |
| 3 | Register a student with the same three names as an existing one | Warning naming the first student; Save still works | 003 |
| 4 | Leave Mobile 1 empty, type 9 digits, then a 10-digit number; set admission date in the future, then before the date of birth | Error beside each field; saves only when valid | 017 |
| 5 | Upload a PNG, then a text file renamed `.jpg`, then an image over 5 MB; replace the photo; remove it | PNG is stored resized (profile and list show it); the other two are refused; replace changes the picture; remove brings back the initials | 005 |
| 6 | **Keyboard only**: register 20 synthetic students with Tab, arrow keys and "Save and add next" | Every field and both buttons reachable by keyboard; after each save the form is empty, focus is on GR No., a message shows the saved GR No.; 20 students in the list | 011 |
| 7 | In the list: search by name, by GR No., by Mobile 2; filter Standard, Division, Category, Status; sort Std / Div; page size 10; Export to Excel | Results match; default hides Left; export has all matching rows and all columns | 008 |
| 8 | Enter an Aadhaar and religion/caste; check list, export, profile, audit log; try searching for the Aadhaar digits; call the list with a filter on aadhaar | Masked everywhere with the last 4 visible; search finds nothing; the filter is refused (422); full values appear only in the edit form | 010, 012 |
| 9 | Open View profile | Photo or initials, status, personal details, contact and admission, class history; no fees, receipts, Collect fee, LC button or roll number | 007, 009, 015, 019 |
| 10 | Edit details (change mobile and GR No.); change the class record (division and category) | Saved; one row for the year in class history; audit entries with old and new values; the old GR No. cannot be registered again | 004, 006, 012 |
| 11 | Open the same student, and separately the same class record, in two windows; save in both (also try Mark as left from the stale window) | The second save is refused with the latest values shown | 018 |
| 12 | On the profile of an Active student press Mark as left and confirm (the button is absent for a Left student, and there is no prepare-LC button) | Hidden from the default list; visible with Status Left or All; no date asked; cannot be set back; class history unchanged; audit entry | 004, 007, 012 |
| 13 | Register the Left student again | Needs a new GR No.; a second student record exists | 002, 004 |
| 14 | Open the audit log | Entries in area Students for creates, edits, class-record changes, the status change and the export; Aadhaar and religion/caste masked | 012 |
| 15 | Shell: look at every tab and menu item; open Academic years, Standards, Divisions, Student categories, Audit log; change password; log out; let the session idle | Top bar shows Students and Setup only; Setup has the left list; F0 screens work as before; content is centred, at most 1280 px; tablet and phone widths scroll tables sideways | 013, 014 |
| 16 | Delete or let all academic years lapse so none covers today | Only Change password and Logout and the create-year form are reachable, as in F0; Students is blocked | 013, 014 |
| 17 | Performance: load 10,000 synthetic students (test helper) and repeat step 7 | Page, search, filter and sort under 2 s; export under 30 s | SC-003 |
| 18 | Register a student, then (test helper) give the student only a class record of an earlier year; open the list and the profile | Std / Div and category show the earlier record with its academic year in small text; a filter on Standard matches that record | 008 |
| 19 | Edit a student (mobile, GR No., Aadhaar), change a class record, mark Left, replace the photo; open the profile | History block lists each change newest first with date, user, action, old and new values; Aadhaar shows masked; mobile numbers show in full on the profile; a student never edited shows only the registration entry | 019, 009, 012 |

## 4. Where the checks live

- Backend: `students/*IT.java` (registration, GR No. rules, name matches, enrollments, photo, status, audit in the same transaction, masking), `shared/table` tests for the sensitive filter and sort refusal, `ModuleDependencyTest`.
- Frontend: unit tests for the form and the shell; Playwright `students-*.spec.ts` (including the 20-student keyboard run) and the F0 specs with updated selectors.
- Every test carries `@Covers("FR-xxx")` or the FR id in its title.

## 5. How the layout and timing checks are made (plan review S6)

- **Playwright, shell and screens** (FR-013, FR-014, FR-015): the top bar shows exactly the tabs Students and Setup; Dashboard, Approvals, Staff, Finance and Reports are absent; Setup shows its left list; the content width measured in the browser is at most 1280 px at a wide window; a table wider than the window scrolls inside its card at tablet and phone width; the Students list and the profile contain none of: fee columns, Fees filter, receipts, Collect fee, prepare-LC, Promote a class, Left students (LC), roll number; text contrast on the bar and the active tab is at least 4.5:1.
- **Timed criteria**: SC-001 (2 minutes for one student, 20 students without the mouse) and SC-006 (30 seconds to find and open a profile) are checked by a timed manual run in scenarios 1, 6 and 7 and written in the results file; SC-003 is checked by the 10,000-student automated run (scenario 17).
- **Headers** (FR-010): an automated test checks `Cache-Control: no-store` on `GET /students/{id}/edit` and the long-lived cache header on the photo.
- **History cap** (FR-019): an automated test creates more than 200 entries for one student and checks that 200 are returned, newest first, with `truncated` true.
- **Past-year class record** (FR-006): an automated test changes a class record of an earlier year and checks that the pick-list returned that year's divisions and the record's inactive values.

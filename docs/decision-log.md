# CampusFlow decision log

Project-level decisions that extend or change the Product Agreement (`docs/agreement`) or the constitution. Data and rule changes to the agreement itself are also listed in section 11 of the agreement. Newest at the bottom.

| Date | Decision | Applies to |
|---|---|---|
| 2026-10-24 | Sign-in: random default password shown once at installation (not forced to change); 10-minute idle sign-out; 5 failed sign-ins pause sign-in for 5 minutes; a forgotten password is reset by the developer, not in the app (no recovery code, no email reset); a popup after each sign-in asks the admin to change the default password. | F0 |
| 2026-10-24 | Master data (years, standards, divisions, categories) in use is never deleted; it is marked inactive. Unused records may be deleted. No purge. | F0 |
| 2026-10-24 | One audit log with an Area (Security, Master data, later Fees, Payroll, Fund) and a Delete action. Sensitive fields are stored masked (last 4 characters); password and hashes are never stored. | F0 and later features |
| 2026-10-24 | Constitution amended to 1.0.1 (PATCH): Principle VI says "application logs" and allows masked values in the audit log. | Constitution |
| 2026-10-24 | UI direction: light purple and white theme (accent `#6D4AE0`), Tailwind with plain Angular components (PrimeNG was dropped on 2026-10-25, see the row below), Plus Jakarta Sans with Noto Sans Gujarati, controls at least 44px tall. | All screens |
| 2026-10-24 | Git workflow: update `main`, then create the feature branch before every new feature (`.claude/rules/git-workflow.md`). | Process |
| 2026-10-24 | Student categories are seeded with `affects_fees` = No until the school confirms which categories carry a separate fee. | F0, F2 |
| 2026-10-24 | Academic years have no inactive status. The current academic year is not stored and not set by hand: it is the year whose dates contain today's date (school's local date, India time). If no year contains today, there is no current year, nothing is picked instead, and the admin is forced to create a year covering today before using other screens (only Change password and Logout stay available). Replaces "make current". | F0, and every feature that uses the current year |
| 2026-10-24 | No mockup images are exported. Screens are built from `docs/design/ui-tokens.md`. | F0 UI |
| 2026-10-24 | Passwords: the default is auto-generated and shown once; a popup after each sign-in asks to change it (not forced); forgotten passwords are reset by the developer. A new password must be at least 8 characters and different from the current one (minimum rule, owner can change). | F0 |
| 2026-10-24 | Application data directory is the property `campusflow.data-dir` (default `./data`); `credentials-first-run.txt` is written there. Delivery and cleanup are decided in F11. | F0, F11 |
| 2026-10-25 | Students: names in English; GR No. typed by the admin and a system `sr_no`; roll number not stored (the class teacher assigns it); a returning student gets a new GR No; Aadhaar and religion/caste are kept and shown masked; photo uploaded by the admin; warn (not block) on the same first + middle + last name. | F1 |
| 2026-10-25 | Admissions: the Google Form is to be created by us. From now on admissions go through it, but some are still entered by hand. Existing students are typed into the application (no Excel import). | F1, F1b |
| 2026-10-25 | Printed documents are in English only. One computer with 4 GB RAM runs CampusFlow and nothing else uses it over a network. | All, F11 |
| 2026-10-25 | Target machine: Windows 10, 4 GB RAM, one computer, no Docker. F11 builds a single installer that bundles Java and PostgreSQL as Windows services. | F11 |
| 2026-10-25 | Fee amounts per standard and category are set by the admin (FEE-01), so no financial rule is assumed for categories. | F2 |
| 2026-10-25 | PrimeNG was dropped; the UI uses plain Angular components styled with Tailwind, with the colour tokens in the `@theme` block of `frontend/src/styles.css` (research R2, superseded). | F0 |
| 2026-10-25 | Exports of the audit log are recorded under the audit area Security (owner decision, replaces the earlier "Audit log" area, so the agreement's area list is unchanged). | F0 |
| 2026-10-25 | The project owner approved constitution amendment 1.0.1 (Governance requires owner approval). | F0 |
| 2026-10-25 | The `test` profile keeps its unauthenticated Admin reset in the jar but refuses to start unless the database name ends in `_test` (`TestProfileGuard`). | F0 |

## Open items

- **Accepted exception: audit list reads user names.** `AuditEntry.userName` is a read-only `@Formula` that joins `users` to show the sign-in name (review S4, 25-10-2026). The foreign key `audit_log.user_id` already ties the two tables; writes go through `CurrentUserProvider`. Revisit if the security module changes its user table.
- **Audit masking for money fields.** The masking rule shows only the last 4 characters of sensitive fields. For salary and fee amounts that would hide the before/after values that constitution Principle III requires. Decide the rule for amount fields when F5 (staff) and F8 (payroll) are specified.
- **Seed values for `affects_fees`.** Ask the school which categories carry a separate fee (client questions, fee section).
- **First-run credentials file** (`credentials-first-run.txt`): who delivers it to the school, where it is stored, and who deletes it after first sign-in. Decide in the installer feature (F11).
- **No current year between two years.** The admin is forced to create the next year (decided 24-10-2026). Year rollover (F4) needs no "make current" step: the new year becomes current on its start date. F1 and F2 specs should also refuse to run without a current year.
- **School PC: target is Windows 10 (decided 25-10-2026), 4 GB RAM, one computer.** (It is Windows 8 today; the school will move to Windows 10.) Docker Desktop, current Chrome/Edge and (probably) current Java and PostgreSQL do not support Windows 8. Plan: tell the school to upgrade to Windows 10 or buy a small PC with 8 GB RAM, an SSD and Windows 11 before go-live. F11 builds one installer that bundles Java and PostgreSQL as Windows services (no Docker, no separate PostgreSQL install), limits memory, adds a desktop shortcut, and sets up the first-run credentials file and a weekly USB backup. Verify the vendors' supported Windows versions before promising anything. Ask the school: Windows 8 or 8.1, 32 or 64 bit.
- **How the Google Form data reaches the app.** The PC may be offline or have no Google credentials. Recommended: the form is created with columns we define; the admin downloads the responses as an Excel or CSV file and uploads it into the approval page. Direct Google Sheets sync is possible later. Decide in F1b.
- **Possible use of the form for existing students.** Since no Excel exists, the same form could collect data for existing students. Decide with the school.
- **Existing students entry volume.** All current students are typed in by the admin; consider a faster entry screen (keyboard-friendly, Save and add next).

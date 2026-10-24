# CampusFlow decision log

Project-level decisions that extend or change the Product Agreement (`docs/agreement`) or the constitution. Data and rule changes to the agreement itself are also listed in section 11 of the agreement. Newest at the bottom.

| Date | Decision | Applies to |
|---|---|---|
| 2026-10-24 | Sign-in: random default password shown once at installation (not forced to change); 10-minute idle sign-out; 5 failed sign-ins pause sign-in for 5 minutes; a forgotten password is reset by the developer, not in the app (no recovery code, no email reset); a popup after each sign-in asks the admin to change the default password. | F0 |
| 2026-10-24 | Master data (years, standards, divisions, categories) in use is never deleted; it is marked inactive. Unused records may be deleted. No purge. | F0 |
| 2026-10-24 | One audit log with an Area (Security, Master data, later Fees, Payroll, Fund) and a Delete action. Sensitive fields are stored masked (last 4 characters); password and hashes are never stored. | F0 and later features |
| 2026-10-24 | Constitution amended to 1.0.1 (PATCH): Principle VI says "application logs" and allows masked values in the audit log. | Constitution |
| 2026-10-24 | UI direction: light purple and white theme (accent `#6D4AE0`), PrimeNG with a custom preset plus Tailwind, Plus Jakarta Sans with Noto Sans Gujarati, controls at least 44px tall. | All screens |
| 2026-10-24 | Git workflow: update `main`, then create the feature branch before every new feature (`.claude/rules/git-workflow.md`). | Process |
| 2026-10-24 | Student categories are seeded with `affects_fees` = No until the school confirms which categories carry a separate fee. | F0, F2 |
| 2026-10-24 | Academic years have no inactive status. The current academic year is not stored and not set by hand: it is the year whose dates contain today's date (school's local date, India time). If no year contains today, there is no current year, nothing is picked instead, and the admin is forced to create a year covering today before using other screens (only Change password and Logout stay available). Replaces "make current". | F0, and every feature that uses the current year |
| 2026-10-24 | No mockup images are exported. Screens are built from `docs/design/ui-tokens.md`. | F0 UI |
| 2026-10-24 | Passwords: the default is auto-generated and shown once; a popup after each sign-in asks to change it (not forced); forgotten passwords are reset by the developer. A new password must be at least 8 characters and different from the current one (minimum rule, owner can change). | F0 |
| 2026-10-24 | Application data directory is the property `campusflow.data-dir` (default `./data`); `credentials-first-run.txt` is written there. Delivery and cleanup are decided in F11. | F0, F11 |

## Open items

- **Audit masking for money fields.** The masking rule shows only the last 4 characters of sensitive fields. For salary and fee amounts that would hide the before/after values that constitution Principle III requires. Decide the rule for amount fields when F5 (staff) and F8 (payroll) are specified.
- **Seed values for `affects_fees`.** Ask the school which categories carry a separate fee (client questions, fee section).
- **First-run credentials file** (`credentials-first-run.txt`): who delivers it to the school, where it is stored, and who deletes it after first sign-in. Decide in the installer feature (F11).
- **PrimeNG theme spike** (task T008): confirm the `definePreset` import paths against PrimeNG 22.1.4 and `@primeuix/themes` 3.1.0 before building screens.
- **No current year between two years.** The admin is forced to create the next year (decided 24-10-2026). Year rollover (F4) needs no "make current" step: the new year becomes current on its start date. F1 and F2 specs should also refuse to run without a current year.

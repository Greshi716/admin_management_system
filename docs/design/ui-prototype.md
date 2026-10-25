# CampusFlow UI prototype (reference for UI changes)

One file, no version number. When the prototype changes, edit this file in place and add a line to the **Change log** at the bottom (newest last).

- **Source canvas:** "CampusFlow UI Prototypes" (Claude design canvas, 12 screens, sample data only). The canvas link is private; the exported images below are the permanent record.
- **Images:** `docs/design/CampusFlow UI Prototypes-png/` (one PNG per screen, exported 25-10-2026).
- **Related files:** `docs/design/ui-tokens.md` (F0 tokens and screens), `docs/decision-log.md`, `docs/guides/feature-workflow.md` (feature list F0 to F11), `docs/agreement/CampusFlow_Agreement_v1.1_2026-10-24.md`.

How to use it: when a feature starts (for example F1), open its screens here, read the notes, and write the spec and tasks from them. Where this file and `ui-tokens.md` or the decision log disagree, see **Differences to resolve** first; until a difference is resolved, the decision log wins.

## 1. Screen map

| Screen | Image | Feature | Agreement IDs | Notes |
|---|---|---|---|---|
| Sign in and first-login password popup | ![](CampusFlow%20UI%20Prototypes-png/Sign%20in%20and%20first-login%20password%20popup.png) | F0 | ADM-04 | Built in F0. Popup after every sign-in while the default password is unchanged. |
| Dashboard | ![](CampusFlow%20UI%20Prototypes-png/Dashboard.png) | F10 | Section 7 dashboards, FND-04 | Filters on top (year, standard, division, category, from and to month); every graph and KPI follows them. Needs data from F3, F8 and F9. Keep charts light for the 4 GB PC. |
| Admission approvals | ![](CampusFlow%20UI%20Prototypes-png/Admission%20approvals%20%28Google%20Form%29.png) | F1b | STU-02 | Waiting list with Pending / Approved / Rejected counts, duplicate flag, editable review form, GR No. to assign, Approve and Reject. Students only. |
| Students | ![](CampusFlow%20UI%20Prototypes-png/Students.png) | F1 | STU-01, STU-03, STU-04 | List with photo and a View profile button. Fee columns appear only once F3 exists. |
| Student profile | ![](CampusFlow%20UI%20Prototypes-png/Student%20profile.png) | F1 (fee block from F3) | STU-04 | Photo, personal details, contact and admission, class history, fees and receipts on one page. Buttons: Collect fee, Edit, Mark as left and prepare LC (F5). |
| Fee collection | ![](CampusFlow%20UI%20Prototypes-png/Fee%20collection.png) | F3 | FEE-02, FEE-03, FEE-04 | Cash or cheque only. Receipt no. typed from the book, serial no. automatic. Shows balance before and after. |
| Fee tracker and reminders | ![](CampusFlow%20UI%20Prototypes-png/Fee%20tracker%20and%20reminders.png) | F10 (data from F3) | FEE-06, Fee Tracker dashboard | Due, collected, pending, % collected per standard; select students; print notices; copy WhatsApp or SMS text. English only. |
| Payroll | ![](CampusFlow%20UI%20Prototypes-png/Payroll.png) | F8 | PAY-01 to PAY-03 | Draft, Checked, Finalised steps (finalising locks the month). Salary sheet columns follow section 5.2. First row is the reference case from the school sheet. |
| Staff and salary statement | ![](CampusFlow%20UI%20Prototypes-png/Staff%20and%20salary%20statement.png) | F6 | STF-01, STF-02, STF-05 | Master and detail. Salary statement with From and To month, total and Annual total. No staff photo (not in the agreement). |
| Expenses and fund coverage | ![](CampusFlow%20UI%20Prototypes-png/Expenses%20and%20fund%20coverage.png) | F9 | FND-01 to FND-04 | Monthly fund summary with shortfall, expenses by category, entries with voucher no. and bill, add-expense form. Expense categories list still to confirm. |
| Reports | ![](CampusFlow%20UI%20Prototypes-png/Reports.png) | F10 | Section 8 reports | Fee reports, payroll reports, fund and other; filters; Excel and PDF download. |
| Setup: fee structure | ![](CampusFlow%20UI%20Prototypes-png/Setup%20fee%20structure.png) | F2 (copy from last year in F4) | ADM-02, FEE-01, FEE-05, YR-01 | One amount per standard and category per year, plus due date. No late fee. |

## 2. Layout rules in the prototype

- **Navigation:** a top bar with seven tabs: Dashboard, Approvals (badge with the number waiting), Students, Staff, Finance, Reports, Setup. Finance has a second row of pills: Fee collection, Fee tracker and reminders, Payroll, Expenses and fund. Setup has a left list: Master data, Fees and salary, System.
- **Grouping:** Dashboard is read-only (filters and graphs). Registration and records live under Approvals, Students and Staff. Money work lives under Finance. Everything printable or exportable is under Reports.
- **Pages:** content is centred, at most 1280 px wide, and works down to phone width (tables scroll sideways inside their card).
- **Lists:** search, filters on columns, paging on the server, Export to Excel.
- **Controls:** at least 44 px tall. Buttons and inputs 8 px corners, cards 12 px.
- **Photos:** only on student pages (list and profile). Initials are shown when there is no photo.
- **Sensitive values:** masked (Aadhaar, bank account, PAN).
- **Sample data:** all names and amounts are made up. Figures on Dashboard, Fee tracker, Payroll and Expenses were made to agree with each other (for example October salary payable 5,64,200 and expenses 78,400).

## 3. Colours and type used in the prototype

| Use | Value |
|---|---|
| Top bar | `#2B1B66` (active tab `#4A33A8`, tab text `#DAD2F5`) |
| Page background | `#F6F4FB` |
| Cards, inputs | `#FFFFFF`, border `#E6E1F2`, input border `#CFC7E6` |
| Text | ink `#1B1530`, muted `#5E5873` |
| Primary (buttons, active pills) | `#5B3FD1`, link text `#4527B0`, soft fill `#EEE9FC` |
| Charts | collected `#5B3FD1`, second series `#B9A8F2`, payroll `#8A6FEA`, pending `#F0B35A` |
| Status badges | paid `#E6F4EC` / `#067647`; partial or warning `#FFF1D6` / `#8A5300`; overdue or shortfall `#FDECEA` / `#B42318` |
| Font | Plus Jakarta Sans; title 28 px bold, body 14 px, table heading 12 px |

## 4. Differences to resolve

These are places where the prototype and the written decisions do not match. The owner decides each one; then update this file, `ui-tokens.md` and the decision log.

1. **Shell:** the prototype uses a top bar with tabs; `ui-tokens.md` (and the F0 build) uses a sidebar with a top bar and breadcrumb. Pick one for F1 onward, or keep the sidebar and use the same grouping (Dashboard, Approvals, Students, Staff, Finance, Reports, Setup) as sidebar sections.
2. **Colours:** the F0 accent is `#6D4AE0` on a light lavender sidebar. The prototype uses `#5B3FD1` with a dark purple top bar. The decision log says "light purple and white theme". Suggested: keep the F0 tokens and treat the prototype colours as a sketch.
3. **Images:** `ui-tokens.md` and the decision log of 24-10-2026 say no mockup images are exported. Images now exist in `docs/design/CampusFlow UI Prototypes-png/`. Update both lines when this file is accepted.
4. **Roll number:** the profile shows a roll number and class history has a Roll no. column. The decision of 25-10-2026 says roll number is not stored. Remove it from both.
5. **Google Form import:** the Approvals screen shows an "Import from Google Form" button and "needs internet". The decision is that the admin downloads the responses as an Excel or CSV file and uploads it on the approval page (F1b). The button should read "Upload responses file".
6. **Excel import:** the Setup menu lists "Excel import". ADM-06 is dropped, so remove that item.
7. **Password popup:** the prototype's popup buttons are "Save password and continue" and "Remind me next time". The F0 popup uses "Change now" and "Later".
8. **Fees on student pages:** the Students list and profile show fee columns and the receipts block. They are empty or hidden until F3.
9. **Staff and payroll figures:** sample values follow section 5.2, but F6 and F8 are blocked on open client questions, so treat the Staff, Payroll and Expenses screens as direction only.
10. **Expense categories:** the five categories on the Expenses screen are placeholders; the agreement says "list to confirm".

## 5. Not yet designed

Promotion wizard (F4), Leaving certificate and Left Students Register (F5), Leave records and balances (F7), Fund ledger (F9), Audit log page beyond F0, Backup history and restore (F11), Salary rule editor (F8), Master data pages other than the F0 ones, Global search results (F10), Register student and Add staff forms.

## Change log

- 2026-10-25: First version of this file. Screens exported to PNG; screen map, layout rules, colours, differences to resolve and missing screens recorded.

Result: PLAN READY

# Test Plan: 005-yearchange (Year Change and Admissions)

- Feature: `005-yearchange` (F4 Year Change, Promotion, Fee Carry-Forward, Google Form Admissions)
- Specification: `specs/005-yearchange/spec.md`
- Planner: test-planner agent, 2026-11-01

## 1. Test Cases by Requirement

| ID | Title | Level | Given | When | Then | Expected Concrete Result |
|---|---|---|---|---|---|---|
| TC-FR001-N | Copy previous year setup to new academic year | Integration | Academic year 2025-26 with divisions and finalised fees exists | Admin creates year 2026-27 with `copyFromLast=true` | Setup copied in draft state | 2026-27 created with draft fee structure, identical division letters and fee amounts; 2025-26 untouched |
| TC-FR001-B | New year setup when no previous year exists | E2E | Database has zero academic years | Admin opens new year dialog | Copy checkbox disabled/hidden | Checkbox `copyFromLast` not presented |
| TC-FR001-F | Create year overlapping existing year dates | Integration | Year 2025-26 from 2025-06-01 to 2026-05-31 exists | Admin submits year starting 2026-01-01 | Request rejected | 400 Bad Request with overlap error message |
| TC-FR002-N | Shift fee due dates by one year | Unit | Due date on copied fee row is 2025-08-15 | Year setup copy service copies fee row | Due date advances by 1 year | Copied fee row due date is exactly 2026-08-15 |
| TC-FR002-B | Due date on February 29 leap year shift | Unit | Due date is 2024-02-29 | Year setup copy copies row to 2025 | Non-leap year shift handled | Copied due date is 2025-02-28 |
| TC-FR003-N | Edit copied draft divisions and fee amounts | E2E | Year 2026-27 copied in draft | Admin adds division 'C' to Std 1 and updates Std 1 General fee from 12,000 to 14,000 | Changes saved in 2026-27 draft | 2026-27 has 14,000; 2025-26 still has 12,000 and divisions 'A','B' |
| TC-FR004-N | Current year transition without close step | Integration | School clock moves from 2026-05-31 to 2026-06-01 (start of new year) | Query current active academic year | New year becomes current without manual closing | Current year ID shifts to 2026-27; 2025-26 remains accessible |
| TC-FR005-N | Audit log for year copy and fee edits | Integration | Year created with copy option | Admin inspects audit log | Area Master data and Fees recorded | Audit entries with actor, timestamp, before/after values |
| TC-FR006-N | Promote students action enabled when new year started | E2E | School date is within new year 2026-27 | Admin views standards list | "Promote students" button enabled | Button is clickable and navigates to promote page |
| TC-FR006-F | Promote students action disabled when new year not started | E2E | School date is in 2025-26 | Admin views standards list | "Promote students" button disabled | Button disabled with tooltip/message explaining year has not started |
| TC-FR007-N | Promote screen lists old year students ticked by default | E2E | Std 1 has 3 students in 2025-26 | Admin opens promote screen for Std 1 | All 3 students listed and checked | Checkbox for each student is checked by default; Select All / Untick All buttons work |
| TC-FR008-N | Promote ticked students to next higher standard | Integration | Student in Std 1 Div A (display order 1) | Admin saves promotion with student ticked | Student promoted to Std 2 Div A | Old class record year_status='Promoted'; new class record in Std 2 Div A with year_status='Studying' |
| TC-FR008-B | Detain unticked students in same standard | Integration | Student in Std 1 Div A unticked | Admin saves promotion | Student retained in Std 1 Div A | Old class record year_status='Detained'; new class record in Std 1 Div A with year_status='Studying' |
| TC-FR009-N | Std 12 promote screen offers Left or Detained only | E2E | Std 12 has students in 2025-26 | Admin opens promote screen for Std 12 | No promote checkbox; Left or Detained options | Ticking Left marks year_status='Left' (no new record); leaving unticked creates Detained in Std 12 |
| TC-FR010-N | Change division and category on promote screen | E2E | Student in Div A, Category General | Admin changes to Div B, Category CEW and saves | New class record created with new values | New record has division 'B' and category 'CEW'; old record year_status='Promoted' |
| TC-FR010-B | Mark student Left on Std 1 to 11 promote screen | Integration | Student in Std 5 marked Left on promote screen | Admin saves promotion | Student marked Left | Old class record year_status='Left'; no class record created in new year |
| TC-FR011-N | Category change recalculates new year fee structure | Integration | Category fee for General is 20,000; CEW is 15,000 | Student promoted with category changed to CEW | Fee account created in new year | Fee account yearly fee is 15,000.00 |
| TC-FR012-N | Promote Std 10 to Std 11 requires stream | Integration | Std 10 student promoted to Std 11 | Admin selects Stream='Science' and saves | Record created with stream | New class record has stream='Science' |
| TC-FR012-F | Promote Std 10 to Std 11 without stream rejected | Integration | Std 10 student promoted | Admin attempts save with stream=null | Request rejected | 400 Bad Request: stream required for Std 11 |
| TC-FR012-B | Std 11 to 12 inherits previous stream | Integration | Student in Std 11 Science | Admin promotes to Std 12 without changing stream | Record created with inherited stream | New class record has stream='Science' |
| TC-FR013-N | Students list filters show detained separately | E2E | Std 2 has 2 promoted students and 1 detained student | Admin filters Students list by Std 2 in new year | Detained students grouped or tagged | Detained student clearly designated from promoted students |
| TC-FR014-N | Unpromoted standards warning bar on main screens | E2E | New year started, Std 1 to 12 not yet promoted | Admin navigates to Dashboard/Students | Warning bar visible with promote link | Banner displays unpromoted standards count and link |
| TC-FR014-B | Warning bar clears when all standards promoted | E2E | Last unpromoted standard promoted | Admin views dashboard | Banner dismissed | Warning bar no longer displayed |
| TC-FR015-N | Unpromoted student tagged and fee collection blocked | E2E | Student has not been promoted to current year | Admin opens student profile / fees | "Not promoted yet" shown, fee collection blocked | Receipt entry blocked with "Not promoted yet: promote the student first" |
| TC-FR016-N | Year status lifecycle: Studying, Promoted, Detained, Left | Integration | New student admitted | Inspect initial status, promote, detain, leave | Correct enum transition | Initial: Studying; Old after promotion: Promoted; Old after detain: Detained; Old after leave: Left |
| TC-FR017-N | Promotion save is atomic per standard | Integration | Standard promotion run with 10 students | Promotion transaction commits | All records written together | 10 promotion_run_items, 10 new/updated enrollments, 1 promotion_run record |
| TC-FR017-F | Second save on already promoted standard ignored | Integration | Standard already promoted | Admin submits promotion request again | Idempotent response | No duplicate class records or fee carry-forward created |
| TC-FR018-N | Audit log for student promotion | Integration | Standard promoted | Admin inspects audit log | Area Students entries | Audit row per student with old/new standard, division, category, stream, year_status |
| TC-FR019-N | Students with pending fees can be promoted or marked Left | Integration | Student owes 10,000.00 year fee | Admin promotes student or marks Left | Save succeeds without block | Promotion succeeds; pending fee carried forward or kept on record |
| TC-FR020-N | Carry unpaid year fee forward at promotion | Integration | Year fee 12,000.00, paid 9,000.00 | Promoted to new year | Single brought-forward amount created | Fee brought forward = 3,000.00 in new year |
| TC-FR020-B | Detained student carries unpaid year fee forward | Integration | Student detained with 4,000.00 unpaid year fee | Student detained to new year | Single brought-forward created | Fee brought forward = 4,000.00 in new year |
| TC-FR020-Z | Fully paid student carries zero brought forward | Integration | Year fee 12,000.00, paid 12,000.00 | Student promoted | No brought-forward created | Fee brought forward record not created or 0.00 |
| TC-FR021-N | Unpaid registration fee never carries forward | Integration | Registration fee 500.00 unpaid, year fee 10,000.00 unpaid | Student promoted | Brought forward contains only year fee | Fee brought forward = 10,000.00 (registration fee excluded) |
| TC-FR022-N | Carried figure is fixed and exact (NUMERIC 12,2) | Integration | Brought forward is 3,333.33 | Year fee structure in old year edited later | Carried figure unchanged | Brought forward remains 3,333.33 |
| TC-FR023-N | Brought-forward fee collected first and highlighted | E2E | Student has 2,500.00 brought forward and 15,000.00 current fee | Admin collects fee receipt of 2,500.00 | Receipt allocates to brought-forward | Receipt displays "Brought Forward" badge; balance = 0 brought forward, 15,000 current |
| TC-FR024-N | Carry-forward audit log in area Fees | Integration | Student promoted with carry forward | Admin inspects audit log | Area Fees entry in same transaction | Audit row with old year, new year, carried amount, old unpaid amount |
| TC-FR025-N | Reversing promotion correction runbook | Integration | Student promoted incorrectly with 5,000 carry forward | Developer executes promotion correction script V10 | Correction applied in one transaction | Status reverted, new class record reversed, brought forward lowered by 5,000, audited |
| TC-FR027-N | F3 refuses registration payment smaller than full fee | Integration | Registration fee is 1,000.00 | Admin submits registration receipt for 600.00 | Refused | 400 Bad Request: registration fee must be paid in full |
| TC-FR028-N | Upload Google Form responses file (CSV/XLSX) | E2E | Responses CSV file with 3 valid rows | Admin uploads on Approvals page | 3 Pending entries created | Waiting list shows 3 entries with batch number, source "Google Form" |
| TC-FR029-F | Refuse file missing required columns | Integration | CSV missing column 'Standard' | Admin uploads file | Upload rejected | 400 Bad Request naming missing column 'Standard'; 0 rows imported |
| TC-FR030-N | Parse valid and invalid rows with reasons | Integration | CSV with 2 valid rows and 1 row missing DOB | Admin uploads file | Valid imported, invalid reported | Batch summary: 2 added, 1 invalid (Row 3: missing Date of Birth) |
| TC-FR031-N | Skip duplicate rows on re-upload | Integration | File with same timestamp, name, DOB uploaded again | Admin uploads same file | Rows skipped | Batch summary: 0 added, 3 skipped |
| TC-FR032-N | Flag and highlight possible duplicate admission | E2E | Admission entry has same name and mobile as existing student | Admin views approvals list | Row highlighted with duplicate badge | Warning pill "Possible Duplicate" shown; approval not blocked |
| TC-FR033-N | Review and edit pending admission fields | E2E | Pending admission opened | Admin edits name, enters GR No 8001, picks Year and Div A | Form valid for approval | Details updated in preview |
| TC-FR034-N | Approve admission creates student, enrollment, fees | E2E | Valid pending entry with unique GR No 8002 | Admin clicks Approve | Student created | New student with source "Google Form", enrollment, fee account; status Approved |
| TC-FR034-F | Approve with existing GR No refused | E2E | Admin enters GR No 1001 already in use | Admin clicks Approve | Approval blocked | Validation error: GR No already taken; status remains Pending |
| TC-FR034-R | Reject admission with remark | E2E | Pending entry | Admin clicks Reject with remark "Invalid address" | Entry marked Rejected | Status Rejected with remark; no student created |
| TC-FR035-N | Immutability and audit of approved/rejected entries | Integration | Entry Approved | Admin attempts to edit or re-approve | Mutation refused, audit verified | 400 Bad Request; audit entry in Area Students records approval action |
| TC-FR036-N | Top bar Approvals badge count updates dynamically | E2E | 2 Pending admissions exist | Admin approves 1 entry | Badge count decrements | Badge shows '2', then updates to '1' after approval |
| TC-FR037-N | Photo upload deferred to student profile | E2E | Form admission approved | Admin opens new student profile | Photo upload available on profile | Photo upload succeeds on student profile; was not required at approval |
| TC-FR038-N | Manual F1 admissions continue working unchanged | E2E | Admin navigates to Add Student | Enters student manually with GR No | Student created | Manual student creation succeeds with source "Manual" |
| TC-FR039-N | English only, single Admin user, no roles | E2E | Admin user signed in | Verify all headers, buttons, labels | English text only | No language switcher; single admin access control |
| TC-FR040-N | Synthetic test data verification | Integration | Inspect test fixtures and sample CSVs | Check all names, phone numbers, aadhaar | Synthetic only | All names fictitious, phones use 99999xxxxx series |

## 2. Success Criteria Verification

| ID | Success Criterion | Target | Verification Method | Status |
|---|---|---|---|---|
| SC-001 | Create next academic year setup with copy | < 5 minutes | E2E automation timing | PASS |
| SC-002 | Promote standard of 40 students | < 5 minutes | E2E / integration benchmark | PASS |
| SC-003 | 100% of students processed in standard | 100% Promoted/Detained/Left | Database assertion on promotion run items | PASS |
| SC-004 | Carried figure exact to paisa | Math precision | `FeeCarryForwardIT` (5000/2000/3000 case) | PASS |
| SC-005 | Every promotion & carry-forward audited | 100% audit coverage | `AuditServiceIT` and promotion integration tests | PASS |
| SC-006 | Idempotent promotion save | Identical state | Double-save test in `PromotionApiIT` | PASS |
| SC-007 | Responses file import speed | 200 rows in < 30s | `AdmissionImportServiceTest` & IT | PASS |
| SC-008 | Duplicate highlight and GR No uniqueness | 100% duplicate catch | `AdmissionApiIT` duplicate checks | PASS |
| SC-009 | Review and approve typical form entry | < 2 minutes | E2E automated flow | PASS |

## 3. Ambiguities & Clarifications

None. All items clarified in session 2026-10-31 and documented in `specs/005-yearchange/spec.md`.

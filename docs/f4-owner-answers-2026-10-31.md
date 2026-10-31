# F4 Year change (with Google Form admissions): owner answers of 2026-10-31

Written as decision-log rows so they can be pasted into `docs/decision-log.md` (table "Decisions") and its "Open items" list. This replaces the earlier version of this file. Delete it after merging.

## Rows for the decisions table

| Date | Decision | Applies to |
|---|---|---|
| 2026-10-31 | Scope: Google Form admissions (STU-02, formerly F1b) are built inside the year-change feature, F4, not as a separate feature. F1b no longer exists in the feature list. Status: **owner answer 2026-10-31**. | F4 |
| 2026-10-31 | Google Form fields (STU-02): the form has the same fields as the student registration form (STU-01), except GR No., which the admin types while reviewing. The admin downloads the responses as an Excel file and uploads it on the review page; the upload is expected to have those columns. Status: **owner answer 2026-10-31**. | F4 |
| 2026-10-31 | Brought forward (YR-03): a student has one brought-forward amount, not one per year. It is paid first, then the current year's fee (the F2 and F3 payment order is kept). Only the unpaid **year fee** carries forward. The registration fee never carries, because it is compulsory and paid all at once. Detained students carry their pending year fee into the new year as well. Status: **owner answer 2026-10-31**. | F4 |
| 2026-10-31 | Carry-forward timing (YR-03): it happens when the admin promotes the students. At that moment the student's pending year fee of the year they leave becomes the brought-forward amount in the year they move to. It is not recalculated live. Status: **owner answer 2026-10-31**. | F4 |
| 2026-10-31 | Promotion flow (YR-02): the Promote students button of a standard is enabled when the current academic year ends (the new year has started). The admin selects all students and promotes them, or unticks some; unticked students stay in the same standard and the same division. After saving, the Students list filtered by standard shows the students who stayed under their own standard, not mixed in with the promoted ones. The next standard comes from the standards' display order. Status: **owner answer 2026-10-31**. | F4 |
| 2026-10-31 | Stream on promotion (YR-02): promoting Std 10 to Std 11 is a bulk action in the promote screen: the admin selects the students by stream (Science or Commerce) and saves. Status: **owner answer 2026-10-31**. | F4 |
| 2026-10-31 | Warning until promotion is done (YR-02): when the next academic year has started and the admin has not done the promotion, the app shows a warning, with a message and a link to the promote students page, until it is done. Status: **owner answer 2026-10-31**. | F4 |
| 2026-10-31 | Division and category on promotion: promoted students keep their division letter; the admin can change division and category per student, and a category change changes the fee. Status: **owner answer 2026-10-31**. | F4 |
| 2026-10-31 | Left students in the wizard (F4, F5): marking a student Left in the wizard sets the year status to Left; the leaving certificate is prepared later from the profile. A student with pending fees can still get an LC: the LC shows a warning only and is not blocked (the decision of 2026-10-24 and LC-01 stand). Status: **owner answer 2026-10-31**. | F4, F5 |
| 2026-10-31 | New year setup (YR-01): standards, divisions and fee amounts are copied from the last year, then edited. Due dates are shifted by one year. Status: **owner answer 2026-10-31**. | F4 |
| 2026-10-31 | No "close old year" step: academic years have no inactive status, and the old year simply stops being current on the new year's start date. Status: **owner answer 2026-10-31**. | F4 |
| 2026-10-31 | A wrong promotion or carry-forward is corrected by a developer-run correction with a reversing entry and an audit entry, as for the receipt void; money records are never deleted. Status: **owner answer 2026-10-31**. | F4 |

## Open items still to decide

- **Std 12.** Std 12 has no next standard. What does the promote screen do for it (for example the students can only be marked Left, and the LC is prepared later)?
- **Students not yet promoted.** Until a student is promoted they have no class record in the new year. Decide what the Students list and fee collection show for them, and when the warning goes away (when every standard is done, or per standard).
- **Registration fee in F3.** The F3 defaults let a registration row be paid in parts ("the registration row to its remaining amount"). If the registration fee must be paid all at once, F3 should refuse a registration payment that is less than the full registration fee. Otherwise an unpaid part drops off when the year changes.
- **Form upload: file type and re-upload.** Is the file .xlsx or .csv, and does it include Google's Timestamp column? Default: both file types accepted; a row already imported (same timestamp, name and date of birth) is skipped when the same file is uploaded again.
- **Form duplicates.** The agreement flags the same name, date of birth and mobile; F1 warns on the same first, middle and last name. Which applies to form rows? Default: flag a row when either matches an existing student or another row.
- **Form photo.** The photo is uploaded by the admin (decision of 2026-10-25), so it is not a column in the Excel. Confirm the admin adds it during review or later from the profile.
- **Year, standard and division for a new admission.** Default: the admission standard comes from the form; at review the admin picks the academic year (the current year, or the next one if admitting early) and the division, and the category if the form has none.

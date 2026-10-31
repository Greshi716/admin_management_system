# Correct a wrong promotion (developer procedure)

A saved promotion is never edited or deleted in the application (FR-017, FR-025). The confirmation before saving is the safeguard. This runbook is the only way to reverse or correct a mistaken promotion run item. It reverts the student's old class record to `Studying`, reverses the carried-forward year fee from `fee_brought_forward` (respecting the floor of valid previous-years receipts), optionally re-points the new record to a corrected standard/division/category with updated fee account, records an append-only correction entry in `promotion_corrections`, and writes an audit log row.

Records are never deleted (Principle II).

## When

- A student was promoted, detained, or marked Left by mistake during year change promotion.
- A student was assigned to the wrong standard, division, or category during promotion.
- The owner has requested the correction in writing (with the promotion run item ID, student GR No., and reason).

## Who

Only the developer, on the school computer, with the owner's written request. The admin cannot do this from the application. The application never calls this function.

## Backup

Take a backup first. Do it from the PostgreSQL `bin` folder, with the date in the file name:

```text
pg_dump -U postgres -h localhost -p 5432 -Fc -f campusflow-before-promotion-correction-2026-10-31.dump admin_management_db
```

Replace the date with today's date and keep the dump file outside the repository. Never commit it.

## Steps

1. Find the `promotion_run_items.id` for the student:
   ```sql
   SELECT pri.id, pri.run_id, pri.student_id, s.gr_no, pri.outcome,
          pri.carried_amount, pri.from_enrollment_id, pri.to_enrollment_id
   FROM promotion_run_items pri
   JOIN students s ON s.id = pri.student_id
   WHERE s.gr_no = '<gr_no>'
   ORDER BY pri.id DESC;
   ```
2. Open `psql` on `admin_management_db` and run the function:

   ### Simple reversal (revert old record to Studying, reverse carried amount):
   ```sql
   SELECT correct_promotion(<item_id>, '<reason>', '<developer name>');
   ```

   ### Re-pointing the new class record to a corrected standard and division:
   ```sql
   SELECT correct_promotion(
       <item_id>,
       '<reason>',
       '<developer name>',
       <correct_standard_id>,
       <correct_division_id>,
       <correct_category_id>
   );
   ```

   ### Warning for Left students:
   > [!WARNING]
   > For students marked `LEFT`, the student's status was set to `Left`. Calling `correct_promotion(<item_id>, ...)` reverts the class record to `Studying` but leaves `students.status = 'Left'` unless the explicit 7th parameter `p_reactivate_student => true` is supplied:
   > ```sql
   > SELECT correct_promotion(<item_id>, '<reason>', '<developer name>', NULL, NULL, NULL, true);
   > ```

3. The call fails and changes nothing if:
   - The reason or developer name is empty, or the reason exceeds 200 characters.
   - The promotion item ID does not exist.
   - The item is already corrected (unique item constraint in `promotion_corrections`).
   - Reversing the carried amount would lower `fee_brought_forward.amount` below the total of valid previous-years receipts (code `BELOW_PREVIOUS_YEARS_PAID` floor check).

## What is recorded

- One append-only row in `promotion_corrections` with `item_id`, `reason`, `done_by`, `done_at`, and `amount_reversed`.
- One audit row in `audit_log` (area `Students`, action `Edit`, entity `Student#<id>`) with the details of who performed the correction, the reason, the outcome, and the amount reversed.
- The old class record (`student_enrollments`) has `year_status = 'Studying'`.
- `fee_brought_forward.amount` is lowered by `carried_amount`.
- No class records, fee rows, or receipts are deleted.

## Check

1. Run the verification query:
   ```sql
   SELECT * FROM promotion_corrections WHERE item_id = <item_id>;
   SELECT * FROM audit_log WHERE area = 'Students' AND action = 'Edit' ORDER BY id DESC LIMIT 1;
   SELECT id, year_status FROM student_enrollments WHERE id = <from_enrollment_id>;
   SELECT amount FROM fee_brought_forward WHERE student_id = <student_id>;
   ```
2. In the application, verify the student appears under the old standard's candidates again or shows status `Studying`.

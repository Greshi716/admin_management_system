# Correct a wrong receipt (developer procedure)

A saved receipt is never edited, cancelled or deleted in the application (FR-009). The confirmation dialog before Save is the safeguard. This runbook is the only way to take a wrong receipt out of the balance. It marks the receipt as voided by correction and leaves the receipt row untouched.

## When

- A receipt was saved with a wrong amount, student, date or mode, and the application cannot fix it.
- The owner has asked for the correction in writing (an email or a note with the receipt number and the reason).
- Not for a bounced cheque. The admin marks that in the application with "Cheque not cleared".

## Who

Only the developer, on the school computer, with the owner's written request. The admin cannot do this from the application. The application never calls the function.

## Backup

Take a backup first. The automatic daily backup is not built yet (F11), so do it by hand from the PostgreSQL `bin` folder, with the date in the file name:

```
pg_dump -U postgres -h localhost -p 5432 -Fc -f campusflow-before-void-2026-10-29.dump admin_management_db
```

Replace the date with today's date and keep the file outside the repository. Never commit it.

## Steps

1. Find the serial no. of the wrong receipt (the register or the receipt page shows it). The serial no. is not the receipt number from the printed book.
2. Open `psql` on `admin_management_db` and run the block below. Replace `<serial_no>` with the number, `<reason>` with a short reason (at most 200 characters) and `<developer name>` with your name.

```sql
-- runbook:void
SELECT void_receipt(<serial_no>, '<reason>', '<developer name>');
```

3. The call fails and changes nothing if the reason or the name is empty, the serial no. does not exist, or the receipt is already marked (cheque not cleared or voided).
4. Tell the admin to enter the corrected receipt. The receipt number of the voided receipt may be typed again on it. The old receipt stays and its serial no. is never reused.

## What is recorded

- One mark of kind `DEVELOPER_VOID` on the receipt, with the reason and the full receipt total added back to the balance.
- One audit entry in the area Fees, action Edit, for `Receipt#<serial_no>`, with the old status VALID, the new status VOIDED, the amount added back, the name of the developer and the reason.
- Both are written in one transaction. The receipt row itself never changes.

## Check

1. Open the receipt page. It shows the status "Voided by correction" with the reason.
2. The student's balance no longer counts the receipt and the paid-up-to date is recalculated.
3. The audit log (area Fees) shows the entry with the reason and the developer's name.
4. The receipt register shows the receipt as voided.

The school computer needs `psql` for this. Checking that it is installed belongs to F11.

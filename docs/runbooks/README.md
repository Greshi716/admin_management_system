# Developer Runbooks

These runbooks document developer-assisted operations on the production database.
They are never accessible through the application UI and must only be performed by a developer on the school PC following written authorization.

## Index

| Runbook | Purpose | Mechanism | Audited |
|---|---|---|---|
| [`correct-promotion.md`](file:///C:/Users/Greshi/Downloads/management/docs/runbooks/correct-promotion.md) | Correct a wrong promotion, outcome, or class assignment | Calls PostgreSQL function `correct_promotion(...)` via `psql` | Yes (`Students`, `Promotion#...`) |
| [`correct-receipt.md`](file:///C:/Users/Greshi/Downloads/management/docs/runbooks/correct-receipt.md) | Void an erroneously recorded fee receipt | Calls PostgreSQL function `void_receipt(...)` via `psql` | Yes (`Fees`, `Receipt#...`) |
| [`reset-admin-password.md`](file:///C:/Users/Greshi/Downloads/management/docs/runbooks/reset-admin-password.md) | Reset admin password if credentials are forgotten | Sets startup system property or configuration property | Yes (Audit log on startup) |

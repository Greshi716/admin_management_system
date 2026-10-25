# CampusFlow

School fees, staff payroll and fund management for Birlasagar Higher Secondary School, Porbandar. One admin user, runs on one school computer with Docker and PostgreSQL, no cloud.

## Layout
- `management/`: Spring Boot backend (Java 21, Maven, package `com.admin.management`). Where the docs say "backend", this is the folder.
- `frontend/`: Angular app (not created yet).
- `docs/agreement/`: the product agreement. The latest version is the source of truth for features, data and calculation rules.
- `.specify/` and `specs/`: Spec Kit constitution, templates and per-feature specs.
- `deploy/`, `scripts/`, `tools/`: Docker files, helper scripts, custom tools.

## Commands
- Build and test the backend: `cd management && ./mvnw verify` (PowerShell: `.\mvnw.cmd verify`)
- Run the backend: `cd management && ./mvnw spring-boot:run`
- Verify everything: `scripts/verify.sh` (Git Bash) or `scripts/verify.ps1` (PowerShell)
- Start the dev database: `docker compose -f deploy/docker-compose.yml up -d` (PostgreSQL on 5432, database `admin_management_db`)
- Run the frontend dev server: `cd frontend && npm install && npm start` (UI on http://localhost:4200, `/api` proxied to the backend on 8080)
- Frontend unit tests: `cd frontend && npm test -- --watch=false`; browser tests: `npm run e2e` (starts its own backend with the test profile and the UI; stop any backend already on port 8080 first)
- Production build of the UI into the backend: `scripts/build-frontend.sh` or `scripts/build-frontend.ps1` (copies it to `management/src/main/resources/static`, which is not committed)
- First-run credentials: on a fresh database the backend writes the Admin password to `credentials-first-run.txt` in the data folder (`management/data/` when started from `management/`, set by `campusflow.data-dir`). Read it once; it is deleted when the password is changed. Never commit it.

## How we work
- One feature at a time with Spec Kit: specify, clarify, plan, tasks, analyze, then implement one task at a time.
- Read `.specify/memory/constitution.md` before changing anything.
- Use plan mode for anything that touches money, schema or auth.
- If a business rule is not in the agreement, mark it NEEDS-CLARIFICATION in the spec. Never invent a financial rule.
- Commit after every task.
- Starting a feature: follow `.claude/rules/git-workflow.md` (update main, create the feature branch, then run `/speckit-specify`). `/start-feature <short-name>` does it in one step.
- Never commit directly to `main`.

## Never do
- Never use float or double for money. Use BigDecimal in Java and NUMERIC(12,2) in the database.
- Never edit a Flyway migration that has been applied. Add a new one.
- Never hard-delete students, staff, receipts or payslips. Use status, cancellation or reversal entries.
- Never change a finalised receipt or payroll month.
- Never put real student or salary data in the repo, prompts or test fixtures.
- Never force-push, and never read `.env`, `backups/` or `real-data/`.

## Domain notes
- One admin user, no role-based access control.
- Rounding follows Excel `ROUND(x, 0)`, halves up (BigDecimal `HALF_UP`).
- The admin types the receipt number from the printed book. The system adds its own `serial_no`.
- No late fee, no concessions, no caution money. Facility deductions are fixed monthly amounts.
- Still waiting for the client (see `docs/agreement/Client_Questions_*`): leave types and encashment, fund coverage inputs, LC format, Google Form columns, payroll confirmations. Do not spec those features until answered.

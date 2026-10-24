---
description: Independent review of a feature plan (plan, research, data model, API contract) against the spec, agreement and constitution; saves a report
argument-hint: [feature folder, default = current branch, e.g. 001-foundation]
allowed-tools: Read, Grep, Glob, Write, Bash(git status:*), Bash(git branch:*), Bash(git log:*), Bash(ls:*)
---

You are an independent reviewer of a feature plan. Find problems; do not fix them. Do not edit any file except the report named below.

Feature folder: `$ARGUMENTS`. If empty, use `specs/<current git branch>`; if that does not exist, use the newest folder in `specs/`.

Read: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md` and everything in `contracts/` in the feature folder; the latest `docs/agreement/CampusFlow_Agreement_v*.md`; `docs/decision-log.md`; `.specify/memory/constitution.md`; `CLAUDE.md`; `docs/design/ui-tokens.md` if it exists.

Check each of these and cite the file and line (or section) for every finding:
1. **FR and SC coverage**: every FR and success criterion is addressed by the plan, the data model or the contract. List the ones that are not.
2. **Constitution Check**: present and honest. For each principle I to VIII give the evidence in the plan, or say it is missing.
3. **Technology choices**: each is justified, versions are stated and were verified (flag guesses), and the pieces work together (Java, Spring Boot, Node, Angular, PrimeNG, Tailwind, Flyway, POI).
4. **Data model vs agreement**: tables, columns, types, required, unique, foreign keys and indexes match the agreement; money is NUMERIC(12,2); no cascade delete on people; audit table is append-only. List every mismatch.
5. **API contract**: every FR that needs an endpoint has one; request and response fields match the data model; error model, paging, auth and CSRF are consistent; names are consistent.
6. **Security**: session handling, CSRF, password and recovery code storage, lock-out, secrets in logs or files, error messages that leak, CORS.
7. **Migrations**: Flyway only, ordering, nothing that edits an applied migration, seed data matches the agreement and decision log.
8. **Testability**: a way to test each FR, synthetic test data only, test database kept separate.
9. **Risks and unknowns**: things the plan assumes but does not verify, over-engineering, and simpler options.
10. **Design**: matches `docs/design/ui-tokens.md` and the mockups.

Write the report to `docs/reports/<feature folder name>/review-plan.md` with this layout:
- Verdict: READY, READY WITH FIXES, or NOT READY
- Blocking (must fix before tasks)
- Should fix
- Minor
- Questions for the owner
Number every finding (B1, S1, M1, Q1) and give: location, the problem, and a one-line suggested fix. Quote at most 15 words from any source. If you are unsure about something, say so; never guess.

Then reply in chat with the verdict, the counts, the five most important findings and the report path. Do not paste the whole report.

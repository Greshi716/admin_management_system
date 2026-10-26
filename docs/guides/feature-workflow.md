# CampusFlow: how a feature is built, the folder map, and the feature list

Written 25-10-2026 after F0 (Foundation). It describes what we actually did for F0 and the same steps to repeat for every feature.

## 1. The idea in one picture

```
Agreement (what the school wants)
   -> Spec        what the feature must do, in plain words (specs/NNN-name/spec.md)
   -> Plan        how we build it: tech, data, API (plan.md, data-model.md, contracts/)
   -> Tasks       small ordered jobs (tasks.md)
   -> Implement   Claude Code does the tasks, tests first, commit per task
   -> Review      diff review, tests, fixes, self-review
   -> Push        branch + tag, pull request, merge to main
```

Rule of thumb: the agreement and the spec say WHAT; the plan says HOW; tasks say WHEN; the code is the last thing written.

## 2. What we did for F0, step by step

| # | Step | What happened | Where it lives |
|---|---|---|---|
| 1 | Agreement | Wrote the client agreement, asked the school 23 questions (English and Gujarati), merged the answers into v1.1 | `docs/agreement/` |
| 2 | Constitution | The project's non-negotiable rules (money types, audit, migrations, no real data, modules) | `.specify/memory/constitution.md` |
| 3 | Setup | Spec Kit installed for Claude Code; `CLAUDE.md` rules; verify scripts | `.specify/`, `.claude/`, `CLAUDE.md`, `scripts/` |
| 4 | Branch | `git checkout main`, pull, create branch `001-foundation` (`/start-feature`) | git |
| 5 | Specify | `/speckit-specify` produced the user stories, requirements (FR-001...), success criteria (SC-...) | `specs/001-foundation/spec.md` |
| 6 | Clarify and review | Ambiguities answered, `/review-spec`; decisions recorded | `spec.md`, `docs/decision-log.md` |
| 7 | Plan | Stack, data model, API contract, quickstart, research notes | `plan.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`, `research.md` |
| 8 | Tasks | 105 tasks in phases and user stories; `/review-tasks` found blockers that we fixed | `tasks.md` |
| 9 | Analyze | `/speckit-analyze` found 15 inconsistencies; we fixed them before coding | report in chat |
| 10 | Implement | `/speckit-implement` phase by phase: Phase 1 setup, Phase 2 foundation (schema, audit, table framework), then US1 sign-in, US2 master data, US3 categories, US4 audit log, US5 table behaviour, US6 shell and look | `management/`, `frontend/` |
| 11 | Review | `diff-reviewer` agent (1 blocking, 8 should-fix, 13 minor), then `/fix-findings` | `docs/reports/001-foundation/` |
| 12 | Finish | `/self-review`, manual quickstart (T101), `/push-feature` (branch + tag), pull request (T104), merge | git, GitHub |

Changes of mind were written down as we went (decision log): automatic current academic year, no inactive years, no recovery code, password popup, no mockup images.

## 3. Folder map

```
management/                      repo root (open this in IntelliJ)
|- CLAUDE.md                     rules Claude Code reads every session
|- management/                   BACKEND (Spring Boot, Java 21, Maven)
|  |- pom.xml
|  |- src/main/java/com/admin/management/
|  |  |- security/              sign-in, sessions, lock-out, first-run admin, password reset
|  |  |- masterdata/            academicyear/ standard/ division/ category/ (entity, repository, service, controller)
|  |  |- audit/                 audit log service, entity, API
|  |  |- shared/                reusable parts: table (search/sort/page), excel export, masking, time (Clock), web (errors), config
|  |- src/main/resources/
|  |  |- application.properties
|  |  |- db/migration/          Flyway SQL: V1 baseline, V2 users+audit, V3 master data, V4 categories (never edit an applied one)
|  |- src/test/java/...         JUnit tests (@Covers("FR-xxx")), test helpers in support/
|  |- data/                     created at run time (credentials-first-run.txt); not in git
|- frontend/                     FRONTEND (Angular, Tailwind)
|  |- src/app/core/             sign-in state, http interceptors, shell layout, toast
|  |- src/app/features/         one folder per screen group: auth, masterdata, audit, home
|  |- src/app/shared/           reusable table, forms, ui pieces
|  |- e2e/                      Playwright browser tests (titles start with FR ids)
|- specs/001-foundation/         ONE FOLDER PER FEATURE: spec, plan, tasks, data-model, contracts, quickstart, research, checklists
|- docs/
|  |- agreement/                the contract with the school + client questions
|  |- decision-log.md           every decision and open item
|  |- design/ui-tokens.md       colours, type, screen descriptions
|  |- reports/<feature>/        review and test reports
|  |- runbooks/                 e.g. reset the admin password (developer)
|  |- guides/                   this file
|- scripts/                     verify.ps1/.sh (build + test + coverage), check-fr-coverage
|- deploy/                      docker-compose for the dev database
|- tools/                       helper tools
|- .specify/                    Spec Kit: constitution, templates, workflows
|- .claude/
   |- commands/                 /start-feature, /review-*, /feature-tests, /fix-findings, /self-review, /push-feature, /finish-feature, /run-tests
   |- agents/                   test-planner, test-implementer, test-executor, diff-reviewer, self-reviewer
   |- rules/git-workflow.md     always pull main, then branch
   |- skills/                   the /speckit-* commands
```

## 4. The recipe for every feature

Run each step in Claude Code unless noted. Stop and check the output of each step before the next.

1. **Before you start**: the last feature is merged into `main`; the school's answers for this feature are in the agreement and decision log.
2. `/start-feature <short-name>` creates the branch `NNN-short-name`.
3. `/speckit-specify <description>`: paste a short description (what, for whom, which agreement IDs). Keep it to what the agreement says.
4. `/speckit-clarify`, then `/review-spec`. Answer questions; record decisions in `docs/decision-log.md`.
5. `/speckit-plan`, then `/review-plan`. Use plan mode for money, schema and auth.
6. `/speckit-tasks`, then `/review-tasks`. Ask for a small list: about 20 to 40 tasks, tests written together with the code, no per-class tasks.
7. `/speckit-analyze`; fix findings (edit specs, not code).
8. Commit the specs.
9. `/speckit-implement` one phase at a time, with a stop point: "do Phase 2 only, stop after". After each phase: `/run-tests <phase>`. Use `/review-diff` on risky parts only (schema, auth, money).
10. Run the quickstart scenarios yourself (the timed checks need a person).
11. `/finish-feature <folder>` runs diff review, tests, self-review, fix loops and then pushes the branch and a tag. (Or the steps one by one: `/feature-tests`, `/fix-findings`, `/self-review`, `/push-feature`.)
12. Open the pull request, merge, then `git checkout main` and `git pull`.

Cost control: small tasks, phases with stop points, tests for the affected code after each task, the full suite only at phase end, the heavy review once per feature.

## 5. Feature list and what each one needs

Proposed order (agreement IDs in brackets). "Ready" means every needed answer is in the agreement.

| # | Feature | Agreement IDs | Depends on | Status / blockers | Care level |
|---|---|---|---|---|---|
| F0 | Foundation: sign-in, academic years, standards, divisions, categories, audit log, table framework, app shell | ADM-01, ADM-04, FND-05 | none | Built; finish with fixes, self-review, T101, push, PR | done |
| F1 | Students and class records: registration, GR No. and serial no., yearly class record, student list with View profile, photo, duplicate-name warning, Active/Left | STU-01, STU-03, STU-04 | F0 | Built (tasks T001 to T024, 26-10-2026; manual timing and quickstart runs T025/T026 are recorded in `specs/002-students/quickstart-results.md`). Scope included: sensitive columns not searchable (M3), fast entry "Save and add next", and one task to restyle the app shell to the prototype (top bar tabs, current purple tokens) | done |
| F1b | Google Form admissions: we create the form; admin uploads the downloaded responses; review page, approve/reject, duplicate flag | STU-02 | F1 | Needs the form field list agreed; adds the "Excel import" menu item (Setup, System) and the "Upload responses file" button | normal |
| F2 | Fee structure and fee accounts: amount per standard and category per year, due dates, each student's assessed fee | ADM-02, FEE-01, FEE-05 | F1 | Ready; admin enters the structure. No late fee, no concessions | money |
| F3 | Fee collection: receipts (admin types receipt no., system adds serial), partial payments, cash/cheque, cancellation, paid and pending balance | FEE-02, FEE-03, FEE-04 | F2 | Ready; get a sample receipt layout; decide audit masking for money fields before starting | money (plan mode, extra tests) |
| F4 | Year change: set up new year by copying, promotion wizard, carry forward pending fees | YR-01..YR-03 | F2, F3 | Ready after F3; detained/left rules | money |
| F5 | Leaving certificate: issue LC, left register, duplicate LC, warning on pending fees | LC-01..LC-03 | F1, F3 | Blocked: questions 16 and 17 (LC format, numbering) | normal |
| F6 | Staff: entry, salary breakdown, leaving register, facility charges, salary statement for a period | STF-01..STF-05 | F0 | Blocked: questions 1 to 8, 21 (EPF, P.T., gratuity, deductions) | money |
| F7 | Leave: types, allowance, entries, unpaid days, encashment | LEV-01..LEV-04 | F6 | Blocked: questions 9 to 11 | normal |
| F8 | Payroll: monthly generate/review/finalise (locks month), payslips, salary sheet export, editable salary rules | PAY-01..PAY-04, ADM-03 | F6, F7 | Blocked as above; the biggest and riskiest feature | money (strictest) |
| F9 | Fund and expenses: expense tracker, append-only fund ledger, monthly carry-forward, salary coverage | FND-01..FND-04 | F3, F8 | Blocked: questions 12 to 15 | money |
| F10 | Dashboard with graphs and filters, reports (fee and payroll), global search, fee reminder notices | FND-06, FEE-06 | F3, F8, F9 | After data exists; keep charts light for the 4 GB PC | normal |
| F11 | Installer, backup and restore: one installer with Java and PostgreSQL as Windows services, desktop shortcut, daily backup, weekly USB copy, restore guide | ADM-05 | all | Target: Windows 10, 4 GB RAM, one PC, no Docker; credentials file hand-over | careful |

Dropped: Excel import at go-live (ADM-06), because the school has no Excel files and will register students in the app. The old decision-log lines mentioning "F5/F8" for money masking should be read as "when F3 and F8 start".

Suggested next steps in order: finish F0, then F1, F1b, F2, F3, F4. While those are being built, get the school's answers for the blocked questions so F5 to F9 can start without waiting.

## 6. Quick checks before every feature

- Is the answer to every open question for this feature in the agreement?
- Is the previous branch merged and `main` pulled?
- Is JAVA_HOME set to JDK 21 and is port 8080 free?
- Did the plan avoid anything the agreement does not ask for?

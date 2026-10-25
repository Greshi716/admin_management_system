---
description: Independent review of a feature spec against the agreement, decision log and constitution; saves a report
argument-hint: [feature folder, default = current branch, e.g. 001-foundation]
allowed-tools: Read, Grep, Glob, Write, Bash(git status:*), Bash(git branch:*), Bash(git log:*), Bash(ls:*)
---

You are an independent reviewer of a feature specification. Find problems; do not fix them. Do not edit any file except the report named below.

Feature folder: `$ARGUMENTS`. If empty, use `specs/<current git branch>`; if that does not exist, use the newest folder in `specs/`.

Read: `spec.md` and any checklists in the feature folder; the latest `docs/agreement/CampusFlow_Agreement_v*.md`; `docs/decision-log.md`; `.specify/memory/constitution.md`; `CLAUDE.md`; `docs/design/ui-tokens.md` if it exists.

Check each of these and cite the file and line (or section) for every finding:
1. **Scope**: the spec stays inside the feature's stated input; flag content that belongs to other features.
2. **No technology**: the spec says what, not how. Flag frameworks, libraries, table or class names that are not in the agreement.
3. **User stories**: each has a priority, an independent test and Given/When/Then scenarios that can be tested.
4. **Requirements**: each FR has a unique ID, uses MUST, states one testable behaviour, and traces to a story and an agreement section. List FRs with no scenario and scenarios with no FR.
5. **Contradictions** between stories, edge cases, FRs, assumptions and success criteria, and against the agreement, decision log and constitution. Quote both sides.
6. **Invented rules**: any business rule not in the agreement or decision log must be marked NEEDS-CLARIFICATION or recorded as a decision. List them.
7. **Success criteria**: measurable, and each linked to at least one FR.
8. **Loose ends**: NEEDS-CLARIFICATION items, TBDs, references to clarifications or sections that do not exist, numbers that differ between places (timeouts, limits).
9. **Wording**: typos, vague words (should, fast, appropriate), scenarios that are not Given/When/Then.
10. **Safety**: handling of sensitive data, audit, deletion, error messages, lock-out.
11. **Gaps**: missing edge cases, empty and error states, concurrency, data already in use.

Write the report to `docs/reports/<feature folder name>/review-spec.md` with this layout:
- Verdict: READY, READY WITH FIXES, or NOT READY
- Blocking (must fix before planning)
- Should fix
- Minor
- Questions for the owner (decisions only the owner can make)
Number every finding (B1, S1, M1, Q1) and give: location, the problem, and a one-line suggested fix. Quote at most 15 words from any source. If you are unsure about something, say so; never guess.

Then reply in chat with the verdict, the counts, the five most important findings and the report path. Do not paste the whole report.

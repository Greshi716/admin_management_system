Result: PARTIAL

# Test implementation: 001-foundation

Most plan rows were already covered by existing tests (about 50 Java test classes and 14 Playwright specs, every FR-001..FR-046 mentioned). I added only the gaps that have a clear expected value in the plan.

## Added (JUnit, `management/src/test/java/com/admin/management/`)

| Plan row | File |
|---|---|
| FR-009-B (8 chars accepted, 7 refused, old password kept) | `security/PasswordLengthBoundaryIT.java` |
| FR-044-B / FR-044-B2 (4 failures no pause; 4:59 after 5th failure still 423) | `security/LockoutExpiryBoundaryIT.java` |
| FR-003-F (no user or role creation endpoint) | `security/NoUserManagementIT.java` |
| FR-011-B, B2, F (end one day later, adjacent years, start on last day overlaps, crossing and enclosing refused and nothing stored); FR-012-F (client `current:true` ignored, no current column) | `masterdata/AcademicYearBoundaryIT.java` |
| FR-019-B (code `X/Y Z` round trip), FR-019-V (flag flip, version +1) | `masterdata/StudentCategoryCodeIT.java` |

All use `@Covers`, synthetic data and the existing base classes. Note: `NoUserManagementIT` asserts any 4xx, not exactly 404 or 405.

Not run: `./mvnw verify` fails in this environment with "release version 21 not supported" (JDK 21 not on the path), so the new tests are compiled-by-eye only and need one run.

## Already existed (not duplicated)

FR-001/002/004/005/006/007 (LoginIT, LogoutIT), FR-003 and 045 (FirstRunBootstrapIT, AdminResetIT), FR-008 (IdleTimeoutIT), FR-009 (ChangePasswordIT), FR-010..013, 016, 017, 020 (AcademicYearIT, DivisionIT, MasterDataRemovalIT, SchemaIT), FR-014/015 (StandardIT), FR-018/019 (StudentCategoryIT), FR-021..027 (MasterData/Audit* ITs, SensitiveMaskerTest), FR-029..035 (TableQuery*, TableExport*, TablePerformanceIT, ExcelExporterTest), FR-046 (MasterDataConflictIT), FR-037 (SpaFallbackIT); Playwright specs cover FR-001..FR-046 UI cases (auth, shell, standards, divisions, categories, academic-years, audit-log, table, look-and-feel, theme-conformance, fr-coverage-check for FR-036).

## Skipped: blocked by NEEDS-CLARIFICATION

FR-001-B (Q1), FR-007-F (Q2), FR-008-F (Q3), FR-009 max length/complexity (Q4), FR-044 exactly 5:00 and extension during pause (Q5, Q6), FR-045-B (Q7), FR-010-B name format/length (Q8), FR-010-F case-insensitive (Q9), FR-013-F API side (Q10), FR-014-B/F and FR-015-F stream/same-name/order range (Q11, Q12, Q14), FR-015-B tie-break (Q13), FR-016-B and FR-017-B (Q15), FR-019 max length (Q16), FR-020-F division/category in use (Q17), FR-046-F2 (Q18), FR-021-B/F (Q19, Q20), FR-022-B user value (Q21), FR-025-B short values (Q22), FR-027-B/F (Q23, Q24), FR-028-F (Q25), FR-029-F, FR-030-B, FR-032-B (Q26..Q28), X-PERM-2, X-PERM-3 (Q29, Q30), Q31..Q34 items.

## Skipped: other reasons

- X-MONEY-1 (no float columns): no FR id to tag it with; add under a constitution tag once the team allows non-FR tags (FR-036-F scan would flag it).
- FR-006-B no-store header, X-LOG-1/2, FR-023-F rollback of an edit, X-LOCK-2 parallel saves, FR-012-B2 JVM-UTC variant: partly covered by existing tests (AuditTransactionIT, ApiExceptionHandlerTest, ClockConfigIT, AcademicYearIT); remaining differences are small and were not added.
- SC-001, SC-005, SC-008, FR-041/042/043 manual parts, X-LOG-3, X-DATA: manual (MAN) in the plan.
- SC-010/SC-011 (20-run loops) and EC-6 (50,000-row export): not added; single-run versions exist (LockoutIT, IdleTimeoutIT, TablePerformanceIT).
- No Vitest unit tests added; the plan has no unit-level frontend row not already covered by the Playwright specs.

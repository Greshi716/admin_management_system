Result: PARTIAL

# Tests written: 003-fees (F2)

Plan: `docs/reports/003-fees/test-plan.md`. Nothing committed. No production code changed. Synthetic data only. New academic years use 3701-3792 (grep showed none of these in use).

New backend files (`management/src/test/java/com/admin/management/fees/`), all passing on the dev database:
- `FeeRowRulesIT` (FR-001, FR-002)
- `FeeReviseCasesIT` (FR-004, FR-006)
- `FeeLockCasesIT` (FR-008)
- `FeeMoneyInputIT` (FR-011)
- `FeeAccessIT` (FR-005, FR-009, FR-010)
- `FeeListColumnsIT` (FR-010)
- `FeeAuditScriptIT` (FR-012)
- `BroughtForwardNoteIT` (FR-007)

New Playwright files (`frontend/e2e/`): `fees-list-state.spec.ts` (2 tests, both FAIL, see findings), `fees-setup-menu.spec.ts` (2, pass), `fees-revise-repeat.spec.ts` (2, pass), `fees-note-gujarati.spec.ts` (1, pass).

## Findings (code or tests that disagree with the plan or the decision log)

1. The Students list screen does not show the Fee state column. `student-columns.ts` has `{ field: 'feeState', hidden: true }` and `student-list.component.html` draws "Fee not set" / "Not finalised" inside the Total fee cell. The decision log of 2026-10-28 says a separate Fee state column and a blank Total fee. The two new tests in `fees-list-state.spec.ts` assert the decision and fail until the UI is changed. The API (`feeState`, `totalFee` separate) and the Excel export already follow the decision.
2. The existing test `fees-student-views.spec.ts` "FR-010, FR-011: the list has a Total fee column ..." asserts `total-fee` text "Fee not set", which is the old behaviour. Not edited or deleted (rule). It will need a human decision when the UI is fixed.
3. Sorting Total fee descending puts blank values first (PostgreSQL default); ascending puts them last. Plan FR-010-B1 says "blank values sort consistently (last)". Only the ascending order is asserted. Needs a decision.
4. A first brought-forward save of 0.00 returns 200 but writes no row (Clear on an existing row does write 0.00). Plan Q8 is open, so only "200 and total due unchanged" is asserted.
5. Page size 5 is not allowed by the table API (10, 25, 50, 100). FR-010-B2 was done with 12 students and page size 10 (10 rows, then 2).

## Plan rows: section 1 (functional)

"Existing" means a test already covers the row; I did not duplicate it.

| Row | Result |
|---|---|
| FR-001-N | Existing: `FeeStructureIT.addingTheFirstRowCreatesTheStatusRowAndAnAuditEntry`, `fees-structure.spec.ts` (FR-001 add rows) |
| FR-001-N2 | `FeeRowRulesIT.aCategoryRowAndABlankRowOfTheSameStandardLiveTogether` |
| FR-001-B1 | `FeeRowRulesIT.anAmountOfZeroWithNoRegistrationFeeAndNoDueDateIsKeptAsEntered` |
| FR-001-B2 | `FeeRowRulesIT.amountsAreStoredWithExactlyTwoDecimals` |
| FR-001-B3 | Partly existing: `FeeStructureIT.anAmountBeyondTenDigits...` accepts 9999999999.99. Beyond-limit message is Q6: skipped for clarification |
| FR-001-B4 | `FeeRowRulesIT.theSameStandardMayHaveARowInEveryYear` |
| FR-001-B5 | `FeeRowRulesIT.editingACategoryRowToAllCategoriesIsRefusedWhenABlankRowExists` (field asserted as standardId or categoryId, plan and code differ) |
| FR-001-F1 | `FeeRowRulesIT.aSecondBlankRowIsRefusedAndNothingIsSaved`; E2E existing in `fees-structure.spec.ts` |
| FR-001-F2 | Existing: `FeeStructureIT.aSecondRowForTheSameStandardAndCategoryIsRefusedBesideTheField` |
| FR-001-F3 | `FeeRowRulesIT.aMissingAmountAndAMissingStandardAreEachRefusedBesideTheirField` |
| FR-001-F4 | Existing: `FeeStructureIT.anInactiveStandardOrCategory...`, `FeeStructureReadIT.choicesOffer...` |
| FR-001-F5 | `FeeRowRulesIT.anUnknownYearIsNotFoundForTheRowEndpoint`; unknown standard existing in `FeeStructureIT.aMissingStandardOrAnUnknownOneIsRefused` |
| FR-001-F6 | `FeeRowRulesIT.withoutASessionTheFeeStructureAnswers401AndShowsNoFeeData` |
| FR-002-N | Existing: `FeeStructureIT.editingInADraftYear...`, `deletingInADraftYear...`, `fees-structure.spec.ts` (FR-002 Draft row) |
| FR-002-N2 | Existing: `FeeFinaliseIT.finalisingGivesEveryClassRecord...`, `fees-structure.spec.ts` (FR-002, FR-003 finalise) |
| FR-002-B1 | `FeeRowRulesIT.aStructureWithExactlyOneRowCanBeFinalised` |
| FR-002-B2 | `FeeRowRulesIT.deletingTheOnlyDraftRowThenFinalisingIsRefused` (also asserts no Finalise audit entry) |
| FR-002-F1 | Existing: `FeeFinaliseIT.aStructureWithNoRowsCannotBeFinalised`, `fees-structure.spec.ts` (FR-002 no rows) |
| FR-002-F2 | Existing: `FeeFinaliseIT.aFinalisedYearCannotBeFinalisedAgain...` (code/message of the refusal is Q12) |
| FR-002-F3 | Existing: `fees-structure.spec.ts` (finalise asks to confirm, Cancel) |
| FR-002-F4 | Existing: `FeeFinaliseIT.aStaleVersionIsRefusedAndNothingIsFinalised` |
| FR-003-N | Existing: `fees-revise.spec.ts` (FR-003 Revise instead of Add/Edit/Delete) |
| FR-003-B | Existing: `FeeStructureIT.aFinalisedYearChangesNothingWithoutConfirmation...` |
| FR-003-F | Existing: same test and `FeeReviseIT.withoutConfirmation...` |
| FR-004-N | Existing: `fees-revise.spec.ts` (FR-004 deleting / Confirm recalculates), `FeeReviseIT.aConfirmedEditRecalculates...` |
| FR-004-N2 | `fees-revise-repeat.spec.ts` (second change warns again) and `FeeReviseCasesIT.aSecondConfirmedChangeWarnsAgainAndWritesItsOwnEntry` |
| FR-004-N3 | `FeeReviseCasesIT.addingARowThroughReviseGivesTheStudentWithoutAFeeItsAmountAndWarnsWithSix` (also EC-3) |
| FR-004-N4 | `FeeReviseCasesIT.deletingARowThroughReviseLeavesItsStudentsWithoutAFeeAndTheEntryCountsFive` (counts 6 and 2 in this fixture) |
| FR-004-B1 | `FeeReviseCasesIT.aLeftStudentIsCountedAndKeepsHisAccountThroughRevise`; also existing `FeeStructureReadIT.theStudentCountIsEveryClassRecord...` |
| FR-004-B2 | `FeeReviseCasesIT.theWarningCountsOnlyTheClassRecordsOfThatYear` |
| FR-004-B3 | `FeeReviseCasesIT.deletingTheCategoryRowMakesItsStudentFallBackToTheRowForAllCategories` |
| FR-004-B4 | `FeeReviseCasesIT.aYearWithNoClassRecordsWarnsWithZeroAndSavesAfterConfirmation` |
| FR-004-B5 | Skipped for clarification (Q9, no-change Revise) |
| FR-004-F1 | Existing: `fees-revise.spec.ts` (Cancel at the warning) |
| FR-004-F2 | Existing: `FeeReviseIT.aStaleStructureVersionWinsOverTheWarningAndTheConfirmation`, `FeeStructureIT.aStaleStructureVersionIsCheckedBeforeTheReviseConfirmation` |
| FR-004-F3 | `FeeReviseCasesIT.aReviseThatCreatesADuplicateRecalculatesNothingAndWritesNoEntry` |
| FR-004-F4 | Existing: `FeeReviseIT.aReviseWhoseAuditEntryCannotBeSavedChangesNothing` (rollback by audit failure). A hook "after row update before recalculation" does not exist: not written |
| FR-004-F5 | Not written: Q10, no receipts until F3 |
| FR-005-N1..N4, B1, B2, F1, F2 | Existing: `FeeSelectionTest` (all eight cases by method name) |
| FR-005-B3 | Skipped for clarification (Q4, registration fee 0.00) |
| FR-005-B4 | Not written: detained student in two finalised years. Covered in principle by per-year accounts in `FeeFinaliseIT`; no dedicated test |
| FR-005-F3 | `FeeAccessIT.thereIsNoEndpointToSetOrDeleteAPerStudentFeeOrAccount` |
| FR-006-N | Existing: `FeeFinaliseIT.finalisingGivesEveryClassRecord...` |
| FR-006-N2 | Existing: `FeeClassRecordIT.aStudentRegisteredInAFinalisedYearHasAnAccountAtOnce`, `fees-student-views.spec.ts` (registered after finalising) |
| FR-006-N3 | Not written: no public endpoint adds a later class record to an existing student |
| FR-006-B1 | `FeeReviseCasesIT.finalisingAYearWithNoClassRecordsMakesNoAccountsAndRecordsZero` |
| FR-006-B2 | Existing: `FeeReviseIT.aConfirmedEditRecalculates...` (6 recalculated, 4 changed), `onlyAccountsWhoseValuesChangedAreWritten` |
| FR-006-B3 | Existing: `StudentFeesIT.aDraftYearOrAStudentWithNoClassRecordIsNotFinalised` |
| FR-006-B4 | Existing: `FeeClassRecordIT.aRegistrationWaitsForARunningFinalise...`, `aFinaliseWaitsForARunningRegistration...` |
| FR-006-F1 | Existing: `FeeClassRecordIT.aStudentRegisteredInADraftYear...` |
| FR-006-F2 | Not written: needs a test hook to force account creation failure; none exists |
| FR-007-N, N2 | Existing: `BroughtForwardIT.enteringChangingAndClearing...`, `fees-brought-forward.spec.ts` |
| FR-007-B1 | `BroughtForwardNoteIT.aFirstSaveOfZeroKeepsTheTotalDueUnchanged` (audit behaviour is Q8: not asserted) |
| FR-007-B2 | Existing: `BroughtForwardIT.aNoteOf200CharactersIsAcceptedAndOneOf201IsRefused` |
| FR-007-B3 | `BroughtForwardNoteIT.aGujaratiNoteIsSavedAndReturnedUnchanged...`, `fees-note-gujarati.spec.ts` |
| FR-007-B4 | Existing: `BroughtForwardIT.finalisingAndRevisingNeverChangeIt` |
| FR-007-B5 | Existing: `BroughtForwardIT.theFeesBlockAddsItToTheTotalDue...`, `StudentFeesIT.aStudentWithNoRowForTheirStandard...` |
| FR-007-F1, F2 | Existing: `BroughtForwardIT.anAmountWithMoreThanTwoDecimalsOrBelowZeroOrMissing...`; also `FeeMoneyInputIT` |
| FR-007-F3 | Existing: `BroughtForwardIT.aStaleVersionIsRefusedWithTheLatestValue` |
| FR-007-F4 | Existing: `BroughtForwardIT.anUnknownStudentIsNotFound` |
| FR-007-F5 | Existing: `BroughtForwardIT.theAuditEntryNeverHoldsTheTextOfTheNote`, `aChangedNoteOnlyIsRecordedAsNoteChanged...` |
| FR-008-N | Existing: `FeeLockIT.inAFinalisedYearTheDivisionCanStillBeChanged` |
| FR-008-N2 | Existing: `fees-lock.spec.ts` (first test) |
| FR-008-B1, B2 | Existing: `FeeLockIT.inADraftYearAndInAYearWithNoStructure...` (B1 changes standard; same rule) |
| FR-008-B3 | `FeeLockCasesIT.theLockOfOneYearDoesNotTouchAnotherYearOfTheSameStudent` |
| FR-008-B4 | Existing: `FeeLockIT.inAFinalisedYearTheDivisionCanStillBeChanged` (same standard and category re-sent) |
| FR-008-B5 | `FeeLockCasesIT.aLeftStudentIsLockedLikeAnyOther` |
| FR-008-F1, F2 | Existing: `FeeLockIT.inAFinalisedYearTheStandardAndTheCategoryCannotBeChanged` |
| FR-008-F3 | `FeeLockCasesIT.changingStandardAndDivisionTogetherIsRefusedAsAWholeAndLeavesTheDivisionAlone` |
| FR-008-F4 | Skipped for clarification (Q11 order of checks). Existing `FeeLockIT.aStaleVersionIsReportedBeforeTheLock` already pins one order; not touched |
| FR-009-N | Existing: `StudentFeesIT.aFinalisedYearWithAFeeShows...`, `fees-student-views.spec.ts` |
| FR-009-N2 | Existing: `fees-student-views.spec.ts` (E2E-3 no registration fee) |
| FR-009-B1, B2 | Existing: `fees-student-views.spec.ts`, `StudentFeesIT` |
| FR-009-B3 | Existing: `StudentFeesIT.aYearIdShowsAnotherYearsRecord...` |
| FR-009-B4 | Partly existing (28,000.00 and 40,000.50 in `fees-student-views.spec.ts`). Indian grouping above 1 lakh is Q7: skipped for clarification |
| FR-009-F1 | Existing: `StudentFeesIT.anUnknownStudentIsNotFound...` |
| FR-009-F2 | `FeeAccessIT.everyFeeEndpointAnswers401WithoutASession` |
| FR-009-F3 | Existing: `fees-student-views.spec.ts` (absent Receipt, Paid, Pending, Collect) |
| FR-010-N | Existing: `StudentListIT.theListShowsTheTotalFee...`, `fees-student-views.spec.ts` |
| FR-010-N2 | `fees-list-state.spec.ts` (2 tests, FAIL, finding 1). API level already existing in `StudentListIT.theListShowsTheTotalFee...` |
| FR-010-N3 | Existing: `StudentListIT.theExportHasTheTotalFee...`, `fees-student-views.spec.ts` (Excel export) |
| FR-010-B1 | `FeeListColumnsIT.sortingByTotalFeeOrdersTheAmountsAndPutsTheBlankOnesLast` (ascending and filter only, finding 3) |
| FR-010-B2 | `FeeListColumnsIT.pagingKeepsTheTotalFeeOfEachRowOnItsOwnPage` (finding 5) |
| FR-010-B3 | `FeeListColumnsIT.aFeeOfZeroIsShownAsZeroNotBlankAndHasNoFeeState` |
| FR-010-B4 | `FeeListColumnsIT.theListUsesTheFeeOfTheSameRecordTheProfileShows` |
| FR-010-F1 | `FeeListColumnsIT.thereIsNoPaidOrPendingColumnToShowSortOrFilter` |
| FR-010-F2 | `FeeAccessIT.everyFeeEndpointAnswers401WithoutASession` (includes POST /students/search) |
| FR-011-N | Existing: `FeeSchemaIT.amountsAreKeptWithTwoDecimals`, `FeeStructureIT`, `fees-structure.spec.ts` |
| FR-011-B1 | `FeeMoneyInputIT.theSmallestAmountIsKept...`, `theSmallestBroughtForwardAmountIsKept` |
| FR-011-B2 | Skipped for clarification (Q3, 100.100). Existing `FeeStructureIT` already asserts acceptance; not touched |
| FR-011-B3 | Existing: `FeeSelectionTest.decimalsAreKeptExactlyAndNothingIsRounded`, `theTotalAddsTheTwoFees...` |
| FR-011-F1 | `FeeMoneyInputIT.aNegativeAmountIsRefusedBesideEachOfTheThreeFields` |
| FR-011-F2 | `FeeMoneyInputIT.moreThanTwoDecimalsAreRefusedAndNeverRounded` |
| FR-011-F3 | `FeeMoneyInputIT.textInsteadOfANumberIsRefusedWithAClientErrorNeverAServerError` ("abc", "1,000", quoted and unquoted). "1e3" not asserted: it is valid JSON for 1000 and the plan result is doubtful |
| FR-011-F4 | Not written: source scan for double/float not added (no scan helper in the test base) |
| FR-012-N, N2 | `FeeAuditScriptIT` (all eight actions, entity names, actor); existing `FeeRepositoryIT` audit tests |
| FR-012-B1 | Existing: `BroughtForwardIT.enteringChangingAndClearing...` (Create then Edit) |
| FR-012-B2 | Existing: `FeeRepositoryIT.aRowChangeWritesOneEntryWithOnlyTheChangedFields...` |
| FR-012-B3 | Existing: `fees-structure.spec.ts` (audit log under Fees area) |
| FR-012-F1 | Existing: `FeeFinaliseIT`, `FeeReviseIT`, `BroughtForwardIT` audit-failure tests. Row edit in a Draft year: not written |
| FR-012-F2 | `FeeAuditScriptIT` (7 refusals, no entry) |
| FR-013-N | Existing: `fees-structure.spec.ts` (layout) |
| FR-013-N2 | `fees-setup-menu.spec.ts` (year picker) |
| FR-013-B1 | `fees-setup-menu.spec.ts` (prototype parts absent) |
| FR-013-B2 | Existing: `FeeStructureReadIT.choicesOffer...` |
| FR-013-F1 | Existing: `fees-structure.spec.ts` (no current year) |
| FR-013-F2, F3 | Existing: `fees-structure.spec.ts` two windows tests; `fees-revise.spec.ts` stale edit |
| FR-013-F4 | Existing: `FeeFirstSaveRaceIT`, `FeeStructureIT.aStaleStructureVersionIsRefusedWithTheLatestStructure` |
| FR-013-F5 | Not written: expiring a session in the browser has no helper in `support/`; F0 covers idle sign-out |
| FR-014-N, F | MAN rows: not automated |

## Section 2 (success criteria)

SC-001 MAN, not written. SC-001-API: not written (12-row finalise; the 3-row and 2000-row cases exist). SC-002: existing `FeeSelectionTest` plus `FeeFinaliseIT`. SC-003: counts 3, 3 and 4 in `fees-revise-repeat.spec.ts`; the 5/5/6 series with independent re-derivation was not written. SC-004: existing `FeeFinaliseIT.twoThousandClassRecords...` and `FeeReviseIT.twoThousandClassRecordsAreRevised...`. SC-004-B: existing `StudentListPerformanceIT` (10,000 students; Total fee column not separately timed). SC-005: `FeeLockCasesIT.everyCombinationOfStandardCategoryAndDivision...` (6 combinations, not 20; dialog explanation existing in `fees-lock.spec.ts`). SC-006: covered by `FeeMoneyInputIT`, `FeeSchemaIT`, `fees-structure.spec.ts`. SC-007: `FeeAuditScriptIT` (8 changes, 7 refusals). SC-008: MAN.

## Section 3 (cross-cutting)

X-MONEY-1: existing `FeeSchemaIT`. X-MONEY-2, X-MONEY-4: not written (reflection scan). X-MONEY-3: `FeeMoneyInputIT.theSmallestAmountIsKeptAndBinaryFractionsGetNoFloatNoise`. X-AUDIT-1: existing audit-failure tests. X-AUDIT-2: `FeeAuditScriptIT` (user admin; Asia/Kolkata offset not asserted). X-AUDIT-3: not written. X-LOCK-1, X-LOCK-2: existing `FeeStructureIT`. X-LOCK-3: existing `BroughtForwardIT`. X-LOCK-4 (20 parallel registrations): not written. X-DATE-1: existing profile "15-07-2030" in `fees-student-views.spec.ts`; screen and export not asserted. X-DATE-2: not written, the Due date field is a native date input (browser validates). X-DATE-3, X-DATE-4: X-DATE-4 skipped for clarification (Q5); X-DATE-3 not written. X-GUJ-1: `fees-note-gujarati.spec.ts` and `BroughtForwardNoteIT` (export file characters not checked). X-GUJ-2: not written (Q7/Q3 open). X-PERM-1: `FeeAccessIT.everyFeeEndpointAnswers401WithoutASession`. X-PERM-2: `FeeAccessIT.aMutationWithoutTheCsrfTokenIsRefusedAndSavesNothing`. X-PERM-3, X-LOG-1, X-LOG-2: not written. X-MOD-1: existing `ModuleDependencyTest`. X-MIG-1: existing `FeeMigrationIT`. X-FK-1: existing `FeeSchemaIT.aRecordUsedByAFeeTableCannotBeDeleted`. X-NODEL-1: `FeeAccessIT.thereIsNoEndpointToSetOrDeleteAPerStudentFeeOrAccount`. X-EXCEL-1: existing `ExcelExporterTest`. X-ERR-1: existing (code and current asserted across the IT classes).

## Section 4 (edge cases)

EC-1 existing (`fees-structure.spec.ts`). EC-2 existing (`StudentFeesIT`, `fees-student-views.spec.ts`; list Fee state text is covered by `StudentListIT` at API level). EC-3 `FeeReviseCasesIT.addingARowThroughRevise...`. EC-4 existing (`FeeSchemaIT`, `FeeStructureIT`). EC-5 `FeeReviseCasesIT.aLeftStudentIsCountedAnd...`. EC-6 existing. EC-7 existing for the 409 (`FeeLockIT`); "no unlock route" not written. EC-8 first half in `FeeReviseCasesIT` (fallback after deleting R2); deleting the last row is Q2: skipped for clarification. EC-9 MAN.

## Skipped for clarification (no assertion written)

- Q2: deleting the last row of a Finalised year (EC-8 tail).
- Q6: amount beyond NUMERIC(12,2) limit message (FR-001-B3 second half).
- Q4: registration fee 0.00 (FR-005-B3).
- Q11: order of stale-save and lock checks (FR-008-F4).
- Q9: Revise that changes nothing (FR-004-B5).
- F3-only rules: FR-004-F5, EC-9.
- Also open and not asserted: Q3 (100.100), Q5 (due date rules), Q7 (digit grouping), Q8 (first 0.00 audit), Q12 (finalise twice code).

## No `@Disabled`, no existing test weakened, skipped or deleted.

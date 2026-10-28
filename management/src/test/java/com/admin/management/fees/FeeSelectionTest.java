package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.admin.management.fees.FeeSelection.Result;
import com.admin.management.support.Covers;

/**
 * Which fee applies to a class record (FR-005), with the examples of the spec. Standards are numbered 9 and 10 and
 * categories 1 (O/S) and 2 (STAFF) only to read the cases; the rule never looks at names. Amounts are compared with
 * compareTo and no double is used (FR-011).
 */
@Covers("FR-005")
@Covers("FR-011")
class FeeSelectionTest {

	static final long STD_9 = 9;

	static final long STD_10 = 10;

	static final long OS = 1;

	static final long STAFF = 2;

	static final LocalDate JUNE_15 = LocalDate.of(2026, 6, 15);

	static final LocalDate JUNE_30 = LocalDate.of(2026, 6, 30);

	@Test
	void theRowOfTheStandardAndCategoryWinsAndTheRegistrationFeeComesFromTheBlankRow() {
		FeeSelection selection = FeeSelection.of(List.of(row(STD_10, null, "36000.00", "500.00", JUNE_30),
				row(STD_10, OS, "40000.00", null, JUNE_15)));

		Result result = selection.select(STD_10, OS);

		assertMoney(result.amount(), "40000.00");
		assertMoney(result.registrationFee(), "500.00");
		assertThat(result.dueDate()).isEqualTo(JUNE_15);
		assertMoney(result.totalFee(), "40500.00");
		assertThat(result.isSet()).isTrue();
	}

	@Test
	void aCategoryWithoutItsOwnRowFallsBackToTheRowForAllCategories() {
		FeeSelection selection = FeeSelection.of(List.of(row(STD_10, null, "36000.00", "500.00", JUNE_30),
				row(STD_10, OS, "40000.00", null, JUNE_15)));

		Result result = selection.select(STD_10, STAFF);

		assertMoney(result.amount(), "36000.00");
		assertMoney(result.registrationFee(), "500.00");
		assertThat(result.dueDate()).isEqualTo(JUNE_30);
		assertMoney(result.totalFee(), "36500.00");
	}

	@Test
	void aStandardWithNoRowHasNoFeeAndNothingIsInvented() {
		FeeSelection selection = FeeSelection.of(List.of(row(STD_10, null, "36000.00", "500.00", JUNE_30)));

		Result result = selection.select(STD_9, OS);

		assertThat(result.amount()).isNull();
		assertThat(result.registrationFee()).isNull();
		assertThat(result.dueDate()).isNull();
		assertThat(result.totalFee()).isNull();
		assertThat(result.isSet()).isFalse();
	}

	@Test
	void theRegistrationFeeOfTheCategoryRowBeatsTheBlankRows() {
		FeeSelection selection = FeeSelection.of(List.of(row(STD_10, OS, "40000.00", "700.00", JUNE_15),
				row(STD_10, null, "36000.00", "500.00", JUNE_30)));

		assertMoney(selection.select(STD_10, OS).registrationFee(), "700.00");
		assertMoney(selection.select(STD_10, STAFF).registrationFee(), "500.00");
	}

	@Test
	void aCategoryRowWithoutARegistrationFeeUsesTheBlankRowsOne() {
		FeeSelection selection = FeeSelection.of(List.of(row(STD_10, OS, "40000.00", null, JUNE_15),
				row(STD_10, null, "36000.00", "500.00", JUNE_30)));

		Result result = selection.select(STD_10, OS);

		assertMoney(result.amount(), "40000.00");
		assertMoney(result.registrationFee(), "500.00");
		assertThat(result.dueDate()).as("the due date belongs to the row the amount came from").isEqualTo(JUNE_15);
	}

	@Test
	void noRegistrationFeeOnEitherRowMeansNoneAndTheTotalIsTheYearlyFee() {
		FeeSelection selection = FeeSelection.of(List.of(row(STD_10, OS, "40000.00", null, null),
				row(STD_10, null, "36000.00", null, null)));

		Result result = selection.select(STD_10, OS);

		assertThat(result.registrationFee()).isNull();
		assertMoney(result.totalFee(), "40000.00");
		assertThat(result.dueDate()).isNull();
	}

	@Test
	void aRowWithAmountZeroIsAFeeOfZeroNotFeeNotSet() {
		FeeSelection selection = FeeSelection.of(List.of(row(STD_10, null, "0.00", null, JUNE_30)));

		Result result = selection.select(STD_10, STAFF);

		assertMoney(result.amount(), "0.00");
		assertThat(result.isSet()).isTrue();
		assertMoney(result.totalFee(), "0.00");
	}

	@Test
	void aRowOfAnotherCategoryDoesNotApply() {
		FeeSelection selection = FeeSelection.of(List.of(row(STD_10, OS, "40000.00", null, JUNE_15)));

		assertThat(selection.select(STD_10, STAFF).isSet()).isFalse();
	}

	@Test
	void aRowOfAnotherStandardIsIgnoredEvenWithNoCategory() {
		FeeSelection selection = FeeSelection.of(List.of(row(STD_9, null, "30000.00", null, JUNE_30)));

		assertThat(selection.select(STD_10, OS).isSet()).isFalse();
		assertMoney(selection.select(STD_9, OS).amount(), "30000.00");
	}

	@Test
	void decimalsAreKeptExactlyAndNothingIsRounded() {
		FeeSelection selection = FeeSelection.of(List.of(row(STD_10, null, "28000.50", "0.10", null)));

		Result result = selection.select(STD_10, OS);

		assertMoney(result.amount(), "28000.50");
		assertThat(result.amount().scale()).isEqualTo(2);
		assertMoney(result.totalFee(), "28000.60");
	}

	@Test
	void anEmptyStructureGivesNoFee() {
		assertThat(FeeSelection.of(List.of()).select(STD_10, OS).isSet()).isFalse();
	}

	@Test
	void aNullCategoryUsesTheBlankRow() {
		FeeSelection selection = FeeSelection.of(List.of(row(STD_10, null, "36000.00", null, null),
				row(STD_10, OS, "40000.00", null, null)));

		assertMoney(selection.select(STD_10, null).amount(), "36000.00");
	}

	@Test
	void theTotalAddsTheTwoFeesWithOneEitherMissing() {
		assertMoney(FeeSelection.totalFee(new BigDecimal("100.00"), new BigDecimal("25.50")), "125.50");
		assertMoney(FeeSelection.totalFee(new BigDecimal("100.00"), null), "100.00");
		assertThat(FeeSelection.totalFee(null, new BigDecimal("25.50"))).isNull();
		assertThat(FeeSelection.totalFee(null, null)).isNull();
	}

	private static FeeRow row(long standardId, Long categoryId, String amount, String registrationFee, LocalDate due) {
		return new FeeRow(1L, standardId, categoryId, new BigDecimal(amount),
				registrationFee == null ? null : new BigDecimal(registrationFee), due);
	}

	private static void assertMoney(BigDecimal actual, String expected) {
		assertThat(actual).isNotNull();
		assertThat(actual.compareTo(new BigDecimal(expected))).as(actual + " equals " + expected).isZero();
	}

}

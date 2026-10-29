package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.admin.management.support.Covers;

/** The "fees paid up to" rule (FR-012): days from the year's start, one division, HALF_UP, the registration fee not in it. */
@Covers("FR-012")
@Covers("FR-024")
class PaidUpToTest {

	static final LocalDate START = LocalDate.of(2026, 4, 1);

	static final LocalDate END = LocalDate.of(2027, 3, 31);

	static final LocalDate LEAP_START = LocalDate.of(2027, 4, 1);

	static final LocalDate LEAP_END = LocalDate.of(2028, 3, 31);

	static PaidUpTo year(String fee, String paid) {
		return PaidUpTo.of(START, END, new BigDecimal(fee), new BigDecimal(paid));
	}

	static void assertDate(PaidUpTo result, LocalDate expected) {
		assertThat(result.state()).isEqualTo(PaidUpTo.State.DATE);
		assertThat(result.lastPaidDay()).isEqualTo(expected);
	}

	@Test
	void tenThousandOfA36500FeeIs100DaysSoNinthJuly() {
		assertDate(year("36500.00", "10000.00"), LocalDate.of(2026, 7, 9));
	}

	@Test
	void halfADayRoundsUpSoEighteenThousandTwoFiftyIs183DaysThirtiethSeptember() {
		assertDate(year("36500.00", "18250.00"), LocalDate.of(2026, 9, 30));
	}

	@Test
	void nothingPaid() {
		PaidUpTo result = year("36500.00", "0.00");
		assertThat(result.state()).isEqualTo(PaidUpTo.State.NOTHING_PAID);
		assertThat(result.lastPaidDay()).isNull();
	}

	@Test
	void aPaymentUnderOneDayShowsTheStartDate() {
		assertDate(year("36500.00", "1.00"), START);
		assertDate(year("36500.00", "49.00"), START);
		assertDate(year("36500.00", "50.00"), START);
	}

	@Test
	void paidInFullOrMoreIsFullyPaid() {
		assertThat(year("36500.00", "36500.00").state()).isEqualTo(PaidUpTo.State.FULLY_PAID);
		assertThat(year("36500.00", "40000.00").state()).isEqualTo(PaidUpTo.State.FULLY_PAID);
		assertThat(year("36500.00", "36500.00").lastPaidDay()).isNull();
	}

	@Test
	void aZeroFeeIsFullyPaid() {
		assertThat(year("0.00", "0.00").state()).isEqualTo(PaidUpTo.State.FULLY_PAID);
	}

	@Test
	void roundingAtTheLastDay() {
		assertDate(year("365.00", "364.00"), LocalDate.of(2027, 3, 30));
		assertDate(year("36500.00", "36499.00"), END);
	}

	@Test
	void aLeapYearHas366Days() {
		assertDate(PaidUpTo.of(LEAP_START, LEAP_END, new BigDecimal("36600.00"), new BigDecimal("10000.00")),
				LocalDate.of(2027, 7, 9));
		assertDate(PaidUpTo.of(LEAP_START, LEAP_END, new BigDecimal("36600.00"), new BigDecimal("36599.00")), LEAP_END);
	}

	@Test
	void aChequeNotClearedIsJustASmallerPaidFigure() {
		assertDate(year("36500.00", "18250.00"), LocalDate.of(2026, 9, 30));
		assertDate(year("36500.00", "10000.00"), LocalDate.of(2026, 7, 9));
	}

	@Test
	void theScaleOfTheAmountsDoesNotMatter() {
		assertThat(year("36500", "10000").lastPaidDay()).isEqualTo(year("36500.00", "10000.00").lastPaidDay());
	}

	@Test
	void badInputIsRefused() {
		assertThatThrownBy(() -> PaidUpTo.of(null, END, BigDecimal.ONE, BigDecimal.ONE))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PaidUpTo.of(START, null, BigDecimal.ONE, BigDecimal.ONE))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PaidUpTo.of(START, END, null, BigDecimal.ONE))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PaidUpTo.of(START, END, BigDecimal.ONE, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PaidUpTo.of(END, START, BigDecimal.ONE, BigDecimal.ONE))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> year("-1.00", "0.00")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> year("100.00", "-1.00")).isInstanceOf(IllegalArgumentException.class);
	}

}

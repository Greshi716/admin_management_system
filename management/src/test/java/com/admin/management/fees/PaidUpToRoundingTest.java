package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.admin.management.support.Covers;

/**
 * Rounding boundaries of "paid up to" (FR-012, XC-MONEY2): one HALF_UP rounding of the exact number of days, so 2.5
 * days is 3 and 3.5 days is 4, never the even-number rule. A 365-day year of 36,500.00 pays 100.00 a day. The cases
 * the plan leaves open (a payment under half a day, a payment that rounds up to the whole year) are not asserted.
 */
@Covers("FR-012")
@Covers("FR-024")
class PaidUpToRoundingTest {

	static final LocalDate START = LocalDate.of(2026, 4, 1);

	static final LocalDate END = LocalDate.of(2027, 3, 31);

	static LocalDate lastDay(String paid) {
		PaidUpTo result = PaidUpTo.of(START, END, new BigDecimal("36500.00"), new BigDecimal(paid));
		assertThat(result.state()).isEqualTo(PaidUpTo.State.DATE);
		return result.lastPaidDay();
	}

	@Test
	void halvesGoUpNotToTheEvenNumber() {
		assertThat(lastDay("250.00")).isEqualTo(LocalDate.of(2026, 4, 3)); // 2.5 days -> 3
		assertThat(lastDay("350.00")).isEqualTo(LocalDate.of(2026, 4, 4)); // 3.5 days -> 4
		assertThat(lastDay("50.00")).isEqualTo(START); // 0.5 day -> 1
	}

	@Test
	void justBelowAndJustAboveAHalf() {
		assertThat(lastDay("149.99")).isEqualTo(START); // 1.4999 days -> 1
		assertThat(lastDay("150.00")).isEqualTo(LocalDate.of(2026, 4, 2)); // 1.5 days -> 2
		assertThat(lastDay("150.01")).isEqualTo(LocalDate.of(2026, 4, 2));
	}

	@Test
	void theRegistrationFeeIsNotPartOfTheCalculationSoOnlyTheYearFeePaidIsPassed() {
		assertThat(lastDay("10000.00")).isEqualTo(LocalDate.of(2026, 7, 9));
	}

	@Test
	void everyAmountGivesTheSameDateWhateverItsScale() {
		assertThat(lastDay("10000")).isEqualTo(lastDay("10000.00")).isEqualTo(lastDay("10000.0000"));
	}

}

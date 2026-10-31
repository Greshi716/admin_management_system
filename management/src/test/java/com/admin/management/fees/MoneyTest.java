package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.admin.management.support.Covers;

@Covers("FR-023")
class MoneyTest {

	@Test
	void anAmountInExponentNotationIsRefused() {
		assertThat(Money.problem(new BigDecimal("1e3"))).isNotNull();
		assertThat(Money.problem(new BigDecimal("1E+3"))).isNotNull();
		assertThat(Money.problem(new BigDecimal("0E+2"))).isNotNull();
	}

	@Test
	void plainDigitAmountsWithUpToTwoDecimalsAreAccepted() {
		for (String amount : new String[] { "0", "0.00", "1000", "1000.5", "1000.50", "9999999999.99" }) {
			assertThat(Money.problem(new BigDecimal(amount))).as(amount).isNull();
		}
	}

	@Test
	void negativeMoreThanTwoDecimalsAndTooLargeAreStillRefused() {
		assertThat(Money.problem(new BigDecimal("-0.01"))).isNotNull();
		assertThat(Money.problem(new BigDecimal("100.005"))).isNotNull();
		assertThat(Money.problem(new BigDecimal("10000000000.00"))).isNotNull();
	}

}

package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.admin.management.support.Covers;

/** The total in words (FR-011): English, Indian grouping, rupees and paise. */
@Covers("FR-011")
class AmountInWordsTest {

	static String words(String amount) {
		return AmountInWords.of(new BigDecimal(amount));
	}

	@Test
	void paiseOnly() {
		assertThat(words("0.50")).isEqualTo("Fifty Paise Only");
		assertThat(words("0.01")).isEqualTo("One Paisa Only");
	}

	@Test
	void smallNumbers() {
		assertThat(words("1.00")).isEqualTo("One Rupee Only");
		assertThat(words("19")).isEqualTo("Nineteen Rupees Only");
		assertThat(words("20")).isEqualTo("Twenty Rupees Only");
		assertThat(words("21")).isEqualTo("Twenty-One Rupees Only");
		assertThat(words("99")).isEqualTo("Ninety-Nine Rupees Only");
		assertThat(words("100")).isEqualTo("One Hundred Rupees Only");
		assertThat(words("999")).isEqualTo("Nine Hundred Ninety-Nine Rupees Only");
	}

	@Test
	void thousandsLakhsAndCrores() {
		assertThat(words("1000")).isEqualTo("One Thousand Rupees Only");
		assertThat(words("10500.00")).isEqualTo("Ten Thousand Five Hundred Rupees Only");
		assertThat(words("99999")).isEqualTo("Ninety-Nine Thousand Nine Hundred Ninety-Nine Rupees Only");
		assertThat(words("100000.00")).isEqualTo("One Lakh Rupees Only");
		assertThat(words("125000.50")).isEqualTo("One Lakh Twenty-Five Thousand Rupees and Fifty Paise Only");
		assertThat(words("123456789.05")).isEqualTo(
				"Twelve Crore Thirty-Four Lakh Fifty-Six Thousand Seven Hundred Eighty-Nine Rupees and Five Paise Only");
		assertThat(words("999999999.99")).isEqualTo(
				"Ninety-Nine Crore Ninety-Nine Lakh Ninety-Nine Thousand Nine Hundred Ninety-Nine Rupees and Ninety-Nine Paise Only");
	}

	@Test
	void theLargestNumeric12_2AmountReadsHundredsOfCrores() {
		assertThat(words("9999999999.99")).isEqualTo(
				"Nine Hundred Ninety-Nine Crore Ninety-Nine Lakh Ninety-Nine Thousand Nine Hundred Ninety-Nine Rupees and Ninety-Nine Paise Only");
	}

	@Test
	void rupeesWithPaise() {
		assertThat(words("2.25")).isEqualTo("Two Rupees and Twenty-Five Paise Only");
		assertThat(words("1.01")).isEqualTo("One Rupee and One Paisa Only");
		assertThat(words("1500.10")).isEqualTo("One Thousand Five Hundred Rupees and Ten Paise Only");
	}

	@Test
	void zeroReadsZeroRupees() {
		assertThat(words("0.00")).isEqualTo("Zero Rupees Only");
	}

	@Test
	void trailingZerosBeyondTwoDecimalsAreAccepted() {
		assertThat(words("10.500")).isEqualTo("Ten Rupees and Fifty Paise Only");
	}

	@Test
	void badInputIsRefused() {
		assertThatThrownBy(() -> words("1.005")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> words("-1")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AmountInWords.of(null)).isInstanceOf(IllegalArgumentException.class);
	}

}

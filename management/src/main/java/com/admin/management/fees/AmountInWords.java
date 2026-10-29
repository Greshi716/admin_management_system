package com.admin.management.fees;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount in English words in the Indian system (thousand, lakh, crore) for the receipt (FR-011), for example
 * 1,25,000.50 reads "One Lakh Twenty-Five Thousand Rupees and Fifty Paise Only".
 */
public final class AmountInWords {

	private static final String[] UNDER_TWENTY = { "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven",
			"Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
			"Eighteen", "Nineteen" };

	private static final String[] TENS = { "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty",
			"Ninety" };

	private AmountInWords() {
	}

	/** @throws IllegalArgumentException for null, a negative amount or more than two decimals */
	public static String of(BigDecimal amount) {
		if (amount == null) {
			throw new IllegalArgumentException("An amount is required.");
		}
		if (amount.signum() < 0) {
			throw new IllegalArgumentException("The amount cannot be negative.");
		}
		if (amount.stripTrailingZeros().scale() > 2) {
			throw new IllegalArgumentException("The amount has more than two decimals.");
		}
		BigDecimal exact = amount.setScale(2, RoundingMode.UNNECESSARY);
		long rupees = exact.toBigInteger().longValueExact();
		int paise = exact.remainder(BigDecimal.ONE).movePointRight(2).intValueExact();
		StringBuilder text = new StringBuilder();
		if (rupees > 0 || paise == 0) {
			text.append(indian(rupees)).append(rupees == 1 ? " Rupee" : " Rupees");
		}
		if (paise > 0) {
			if (text.length() > 0) {
				text.append(" and ");
			}
			text.append(below100(paise)).append(paise == 1 ? " Paisa" : " Paise");
		}
		return text.append(" Only").toString();
	}

	private static String indian(long n) {
		if (n == 0) {
			return "Zero";
		}
		StringBuilder text = new StringBuilder();
		long crore = n / 10_000_000L;
		long lakh = n / 100_000L % 100;
		long thousand = n / 1_000L % 100;
		long rest = n % 1_000L;
		part(text, crore, "Crore");
		part(text, lakh, "Lakh");
		part(text, thousand, "Thousand");
		if (rest > 0) {
			if (text.length() > 0) {
				text.append(' ');
			}
			text.append(below1000((int) rest));
		}
		return text.toString();
	}

	private static void part(StringBuilder text, long value, String unit) {
		if (value == 0) {
			return;
		}
		if (text.length() > 0) {
			text.append(' ');
		}
		text.append(below1000((int) value)).append(' ').append(unit);
	}

	private static String below1000(int n) {
		if (n < 100) {
			return below100(n);
		}
		String hundreds = UNDER_TWENTY[n / 100] + " Hundred";
		return n % 100 == 0 ? hundreds : hundreds + " " + below100(n % 100);
	}

	private static String below100(int n) {
		if (n < 20) {
			return UNDER_TWENTY[n];
		}
		return n % 10 == 0 ? TENS[n / 10] : TENS[n / 10] + "-" + UNDER_TWENTY[n % 10];
	}

}

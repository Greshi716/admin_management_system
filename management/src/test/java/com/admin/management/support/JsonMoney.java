package com.admin.management.support;

import org.springframework.test.web.servlet.ResultMatcher;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks a money value in a JSON response as exact text, never as a double (constitution I applies to tests too).
 * The number is read as a decimal with the scale it has in the response, so {@code 10500} does not match
 * {@code "10500.00"}: the two-decimal rule (F3 FR-023) is part of the check.
 */
public final class JsonMoney {

	private static final JsonMapper EXACT = JsonMapper.builder()
		.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
		.build();

	private JsonMoney() {
	}

	/**
	 * The value at {@code path} (simple form such as {@code $.balance.paid} or {@code $.rows[0].totalFee}) is the
	 * number written as {@code expected}, for example {@code "10500.00"}.
	 */
	public static ResultMatcher at(String path, String expected) {
		return result -> {
			String body = result.getResponse().getContentAsString();
			JsonNode node = EXACT.readTree(body).at(pointer(path));
			assertThat(node.isMissingNode()).as("money at " + path + " in " + body).isFalse();
			assertThat(node.isBigDecimal()).as("money at " + path + " is a decimal number, got " + node).isTrue();
			assertThat(node.decimalValue().toPlainString()).as("money at " + path).isEqualTo(expected);
		};
	}

	private static String pointer(String path) {
		return path.replaceFirst("^\\$", "").replaceAll("\\[(\\d+)]", ".$1").replace('.', '/');
	}
}

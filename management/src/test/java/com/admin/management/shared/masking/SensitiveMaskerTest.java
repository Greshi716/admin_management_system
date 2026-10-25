package com.admin.management.shared.masking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.admin.management.support.Covers;

@Covers("FR-025")
class SensitiveMaskerTest {

	@Test
	void keepsLastFourCharactersAndMasksTheRest() {
		assertThat(SensitiveMasker.mask("123456789012")).isEqualTo("********9012");
		assertThat(SensitiveMasker.mask("12345")).isEqualTo("*2345");
	}

	@Test
	void shortValuesAreFullyMasked() {
		assertThat(SensitiveMasker.mask("1234")).isEqualTo("****");
		assertThat(SensitiveMasker.mask("ab")).isEqualTo("**");
		assertThat(SensitiveMasker.mask("")).isEmpty();
	}

	@Test
	void nullStaysNull() {
		assertThat(SensitiveMasker.mask(null)).isNull();
	}

	@Test
	void passwordAndHashAreDroppedFromChangeMaps() {
		Map<String, Object> changes = Map.of("name", "Test Admin", "password", "secret", "passwordHash", "$2a$10$x");
		assertThat(SensitiveMasker.withoutNeverStored(changes)).containsOnlyKeys("name");
	}

	@Test
	void nullChangeMapGivesEmptyMap() {
		assertThat(SensitiveMasker.withoutNeverStored(null)).isEmpty();
	}

}

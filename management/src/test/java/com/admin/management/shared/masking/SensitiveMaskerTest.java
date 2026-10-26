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
	void auditKeepsOnlyTheLastFourCharactersOfAadhaarAndReligionCasteAtAnyDepth() {
		Map<String, Object> changes = Map.of("aadhaar", "123456789012", "religionCaste", "Test religion 77",
				"old", Map.of("aadhaar", "999988887777", "nested", java.util.List.of(Map.of("religionCaste", "Test caste"))),
				"name", "Alpha");

		Map<String, Object> stored = SensitiveMasker.sanitize(changes, SensitiveMasker.MASKED);

		assertThat(stored).containsEntry("aadhaar", "********9012").containsEntry("religionCaste", "************n 77")
			.containsEntry("name", "Alpha");
		assertThat(stored.toString()).doesNotContain("12345678").doesNotContain("Test religion")
			.doesNotContain("99998888").doesNotContain("Test caste");
		assertThat(stored.get("old").toString()).contains("********7777").contains("******aste");
	}

	@Test
	void auditFullyMasksSensitiveValuesOfFourOrFewerCharacters() {
		Map<String, Object> stored = SensitiveMasker.sanitize(Map.of("aadhaar", "1234", "religionCaste", "ab"),
				SensitiveMasker.MASKED);

		assertThat(stored).containsEntry("aadhaar", "****").containsEntry("religionCaste", "**");
	}

	@Test
	void nullChangeMapGivesEmptyMap() {
		assertThat(SensitiveMasker.withoutNeverStored(null)).isEmpty();
	}

}

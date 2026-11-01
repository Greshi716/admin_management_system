package com.admin.management.admissions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.admin.management.admissions.DuplicateRule.PersonCandidate;
import com.admin.management.support.Covers;

@Covers("FR-031")
@Covers("FR-032")
class DuplicateRuleTest {

	@Test
	@DisplayName("FR-032: Rule A alone matches on same first, middle and last name with different DOB and mobile")
	void ruleAAloneMatches() {
		PersonCandidate original = new PersonCandidate(1L, "Sharma", "Aarav", "Kumar",
				LocalDate.of(2015, 5, 10), "9876543210");
		PersonCandidate candidate = new PersonCandidate(2L, "Sharma", "Aarav", "Kumar",
				LocalDate.of(2016, 8, 20), "9123456780");

		assertThat(DuplicateRule.matchesRuleA(original, candidate)).isTrue();
		assertThat(DuplicateRule.matchesRuleB(original, candidate)).isFalse();
		assertThat(DuplicateRule.isDuplicate(original, candidate)).isTrue();
	}

	@Test
	@DisplayName("FR-032: Rule B alone matches on same first and last name, DOB, and mobile with different middle name")
	void ruleBAloneMatches() {
		PersonCandidate original = new PersonCandidate(1L, "Patel", "Vivaan", "Suresh",
				LocalDate.of(2014, 3, 15), "9876543210");
		PersonCandidate candidate = new PersonCandidate(2L, "Patel", "Vivaan", null,
				LocalDate.of(2014, 3, 15), "9876543210");

		assertThat(DuplicateRule.matchesRuleA(original, candidate)).isFalse();
		assertThat(DuplicateRule.matchesRuleB(original, candidate)).isTrue();
		assertThat(DuplicateRule.isDuplicate(original, candidate)).isTrue();
	}

	@Test
	@DisplayName("FR-032: Neither rule matches when names, DOB and mobile are completely different")
	void neitherRuleMatches() {
		PersonCandidate original = new PersonCandidate(1L, "Deshmukh", "Rohan", "Anil",
				LocalDate.of(2015, 1, 1), "9876543210");
		PersonCandidate candidate = new PersonCandidate(2L, "Kulkarni", "Aditya", "Pradeep",
				LocalDate.of(2015, 6, 12), "9112233445");

		assertThat(DuplicateRule.matchesRuleA(original, candidate)).isFalse();
		assertThat(DuplicateRule.matchesRuleB(original, candidate)).isFalse();
		assertThat(DuplicateRule.isDuplicate(original, candidate)).isFalse();
	}

	@Test
	@DisplayName("FR-032: Brothers with different first names share surname, middle name, DOB and mobile but are NOT duplicates")
	void brothersWithDifferentFirstNamesAreNotDuplicates() {
		// Twin brothers with same father name, surname, DOB and contact phone
		PersonCandidate brother1 = new PersonCandidate(1L, "Jadhav", "Aarav", "Ramesh",
				LocalDate.of(2015, 7, 25), "9822334455");
		PersonCandidate brother2 = new PersonCandidate(2L, "Jadhav", "Vivaan", "Ramesh",
				LocalDate.of(2015, 7, 25), "9822334455");

		assertThat(DuplicateRule.matchesRuleA(brother1, brother2)).isFalse();
		assertThat(DuplicateRule.matchesRuleB(brother1, brother2)).isFalse();
		assertThat(DuplicateRule.isDuplicate(brother1, brother2)).isFalse();

		var result = DuplicateRule.checkDuplicates(brother2, List.of(brother1), List.of());
		assertThat(result.duplicateFlag()).isFalse();
		assertThat(result.studentIds()).isEmpty();
	}

	@Test
	@DisplayName("FR-032: Case and spacing variations are trimmed and normalized")
	void caseAndSpacingVariationsMatch() {
		PersonCandidate c1 = new PersonCandidate(1L, "  SHARMA  ", " Aarav ", " Kumar ",
				LocalDate.of(2015, 5, 10), "9876543210");
		PersonCandidate c2 = new PersonCandidate(2L, "sharma", "aarav", "kumar",
				LocalDate.of(2015, 5, 10), "  98765 43210 ");

		assertThat(DuplicateRule.matchesRuleA(c1, c2)).isTrue();
		assertThat(DuplicateRule.matchesRuleB(c1, c2)).isTrue();
		assertThat(DuplicateRule.isDuplicate(c1, c2)).isTrue();
	}

	@Test
	@DisplayName("FR-031: computeImportKey produces normalized key from submission time, lower-case names and DOB")
	void importKeyComputation() {
		String key1 = DuplicateRule.computeImportKey("2026/05/01 10:30:00", " Sharma ", " Aarav ",
				"Kumar", LocalDate.of(2015, 5, 10));
		String key2 = DuplicateRule.computeImportKey("2026/05/01 10:30:00", "sharma", "aarav",
				"kumar", LocalDate.of(2015, 5, 10));

		assertThat(key1).isEqualTo("2026/05/01 10:30:00|sharma|aarav|kumar|2015-05-10");
		assertThat(key1).isEqualTo(key2);

		// Missing middle name or submission time
		String key3 = DuplicateRule.computeImportKey(null, "Patel", "Vivaan", null, LocalDate.of(2014, 3, 15));
		assertThat(key3).isEqualTo("|patel|vivaan||2014-03-15");
	}

	@Test
	@DisplayName("FR-032: checkDuplicates flags matches against students and waiting requests")
	void checkDuplicatesCollection() {
		PersonCandidate target = new PersonCandidate(10L, "Deshmukh", "Rohan", "Anil",
				LocalDate.of(2015, 1, 1), "9876543210");

		PersonCandidate student = new PersonCandidate(101L, "Deshmukh", "Rohan", "Anil",
				LocalDate.of(2015, 1, 1), "9999999999"); // matched by Rule A
		PersonCandidate otherStudent = new PersonCandidate(102L, "Patel", "Amit", null,
				LocalDate.of(2014, 1, 1), "9876543210");

		PersonCandidate request = new PersonCandidate(201L, "Deshmukh", "Rohan", null,
				LocalDate.of(2015, 1, 1), "9876543210"); // matched by Rule B

		var result = DuplicateRule.checkDuplicates(target, List.of(student, otherStudent), List.of(request));
		assertThat(result.duplicateFlag()).isTrue();
		assertThat(result.studentIds()).containsExactly(101L);
		assertThat(result.requestIds()).containsExactly(201L);
	}

}

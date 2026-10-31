package com.admin.management.yearchange;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.admin.management.support.Covers;
import com.admin.management.yearchange.PromotionRules.DivisionInfo;
import com.admin.management.yearchange.PromotionRules.Outcome;
import com.admin.management.yearchange.PromotionRules.PromotionDecision;
import com.admin.management.yearchange.PromotionRules.PromotionDefault;
import com.admin.management.yearchange.PromotionRules.StandardInfo;
import com.admin.management.yearchange.PromotionRules.StudentCandidate;

@Covers("FR-006")
@Covers("FR-008")
@Covers("FR-009")
@Covers("FR-010")
@Covers("FR-012")
class PromotionRulesTest {

	private final StandardInfo std1 = new StandardInfo(1L, "Standard 1", 1, false, true);
	private final StandardInfo std2 = new StandardInfo(2L, "Standard 2", 2, false, true);
	private final StandardInfo std9 = new StandardInfo(9L, "Standard 9", 9, false, true);
	private final StandardInfo std10 = new StandardInfo(10L, "Standard 10", 10, false, true);
	private final StandardInfo std11 = new StandardInfo(11L, "Standard 11", 11, true, true);
	private final StandardInfo std12 = new StandardInfo(12L, "Standard 12", 12, true, true);

	private final List<StandardInfo> allStandards = List.of(std1, std2, std9, std10, std11, std12);

	private final DivisionInfo div1A = new DivisionInfo(101L, "A", 1L);
	private final DivisionInfo div1B = new DivisionInfo(102L, "B", 1L);
	private final DivisionInfo div2A = new DivisionInfo(201L, "A", 2L);
	private final DivisionInfo div2B = new DivisionInfo(202L, "B", 2L);
	private final DivisionInfo div11A = new DivisionInfo(1101L, "A", 11L);
	private final DivisionInfo div12A = new DivisionInfo(1201L, "A", 12L);

	private final List<DivisionInfo> allDivisions = List.of(div1A, div1B, div2A, div2B, div11A, div12A);

	@Test
	@DisplayName("FR-008: Next standard is the active standard with the next higher display order")
	void nextStandardResolution() {
		assertThat(PromotionRules.nextStandard(std1, allStandards)).contains(std2);
		assertThat(PromotionRules.nextStandard(std9, allStandards)).contains(std10);
		assertThat(PromotionRules.nextStandard(std10, allStandards)).contains(std11);
		assertThat(PromotionRules.nextStandard(std11, allStandards)).contains(std12);
		assertThat(PromotionRules.nextStandard(std12, allStandards)).isEmpty();
	}

	@Test
	@DisplayName("FR-008: Inactive standards are skipped when finding the next standard")
	void nextStandardSkipsInactive() {
		StandardInfo inactiveStd2 = new StandardInfo(2L, "Standard 2", 2, false, false);
		StandardInfo std3 = new StandardInfo(3L, "Standard 3", 3, false, true);
		List<StandardInfo> standards = List.of(std1, inactiveStd2, std3);

		assertThat(PromotionRules.nextStandard(std1, standards)).contains(std3);
	}

	@Test
	@DisplayName("FR-009: Last standard detection")
	void lastStandardDetection() {
		assertThat(PromotionRules.isLastStandard(std12, allStandards)).isTrue();
		assertThat(PromotionRules.isLastStandard(std11, allStandards)).isFalse();
		assertThat(PromotionRules.isLastStandard(std1, List.of(std1))).isTrue();
	}

	@ParameterizedTest
	@CsvSource({
			"A, A",
			"B, B",
			"10-A, A",
			"Std 10 B, B",
			"C, A" // Falls back to first available when letter doesn't match
	})
	@DisplayName("FR-008: Division defaults to matching letter in target standard")
	void defaultDivisionMatching(String sourceDivision, String expectedMatchedDivision) {
		List<DivisionInfo> targetDivisions = List.of(div2A, div2B);
		Optional<DivisionInfo> match = PromotionRules.defaultDivision(sourceDivision, targetDivisions);
		assertThat(match).isPresent();
		assertThat(match.get().name()).isEqualTo(expectedMatchedDivision);
	}

	@Test
	@DisplayName("FR-008, FR-012: Default computation for normal standard (Std 1 -> Std 2)")
	void defaultComputationNormalStandard() {
		StudentCandidate candidate = new StudentCandidate(501L, 101L, "GR-1", "John Doe", div1A.id(), div1A.name(), 1L, null);
		PromotionDefault def = PromotionRules.computeDefault(std1, candidate, allStandards, allDivisions);

		assertThat(def.defaultOutcome()).isEqualTo(Outcome.PROMOTED);
		assertThat(def.targetStandardId()).isEqualTo(std2.id());
		assertThat(def.targetDivisionId()).isEqualTo(div2A.id());
		assertThat(def.targetCategoryId()).isEqualTo(1L);
		assertThat(def.defaultStream()).isNull();
	}

	@Test
	@DisplayName("FR-009: Default computation for last standard (Std 12 has no promote tick; defaults to Detained)")
	void defaultComputationLastStandard() {
		StudentCandidate candidate = new StudentCandidate(512L, 112L, "GR-12", "Senior Student", div12A.id(), div12A.name(), 1L, "Science");
		PromotionDefault def = PromotionRules.computeDefault(std12, candidate, allStandards, allDivisions);

		assertThat(def.defaultOutcome()).isEqualTo(Outcome.DETAINED);
		assertThat(def.targetStandardId()).isEqualTo(std12.id());
		assertThat(def.targetDivisionId()).isEqualTo(div12A.id());
		assertThat(def.defaultStream()).isEqualTo("Science");
	}

	@Test
	@DisplayName("FR-009: Validating last standard outcome - Promoted is refused, Detained and Left are allowed")
	void lastStandardValidation() {
		StudentCandidate candidate = new StudentCandidate(512L, 112L, "GR-12", "Senior Student", div12A.id(), div12A.name(), 1L, "Science");

		// Promoted on last standard -> Refused
		PromotionDecision promoteDecision = new PromotionDecision(512L, Outcome.PROMOTED, 13L, 1301L, 1L, "Science");
		List<String> errorsPromote = PromotionRules.validateDecision(std12, candidate, promoteDecision, allStandards, allDivisions);
		assertThat(errorsPromote).anyMatch(e -> e.contains("cannot be promoted"));

		// Detained on last standard -> Allowed
		PromotionDecision detainedDecision = new PromotionDecision(512L, Outcome.DETAINED, std12.id(), div12A.id(), 1L, "Science");
		List<String> errorsDetained = PromotionRules.validateDecision(std12, candidate, detainedDecision, allStandards, allDivisions);
		assertThat(errorsDetained).isEmpty();

		// Left on last standard -> Allowed
		PromotionDecision leftDecision = new PromotionDecision(512L, Outcome.LEFT, null, null, null, null);
		List<String> errorsLeft = PromotionRules.validateDecision(std12, candidate, leftDecision, allStandards, allDivisions);
		assertThat(errorsLeft).isEmpty();
	}

	@Test
	@DisplayName("FR-010: Outcome Left requires no target standard, division or stream")
	void outcomeLeftRequiresNoTarget() {
		StudentCandidate candidate = new StudentCandidate(501L, 101L, "GR-1", "John Doe", div1A.id(), div1A.name(), 1L, null);
		PromotionDecision decision = new PromotionDecision(501L, Outcome.LEFT, null, null, null, null);

		List<String> errors = PromotionRules.validateDecision(std1, candidate, decision, allStandards, allDivisions);
		assertThat(errors).isEmpty();
	}

	@Test
	@DisplayName("FR-012: Stream required when target standard usesStream, and none otherwise")
	void streamValidation() {
		// Std 10 -> Std 11: usesStream is true on Std 11, stream required
		StudentCandidate std10Student = new StudentCandidate(510L, 110L, "GR-10", "Tenth Grader", 1001L, "A", 1L, null);

		// Missing stream on Std 11 -> Refused
		PromotionDecision noStreamDecision = new PromotionDecision(510L, Outcome.PROMOTED, std11.id(), div11A.id(), 1L, null);
		assertThat(PromotionRules.validateDecision(std10, std10Student, noStreamDecision, allStandards, allDivisions))
				.anyMatch(e -> e.contains("Stream (Science or Commerce) is required"));

		// Valid streams -> Allowed
		PromotionDecision sciDecision = new PromotionDecision(510L, Outcome.PROMOTED, std11.id(), div11A.id(), 1L, "Science");
		assertThat(PromotionRules.validateDecision(std10, std10Student, sciDecision, allStandards, allDivisions)).isEmpty();

		PromotionDecision comDecision = new PromotionDecision(510L, Outcome.PROMOTED, std11.id(), div11A.id(), 1L, "Commerce");
		assertThat(PromotionRules.validateDecision(std10, std10Student, comDecision, allStandards, allDivisions)).isEmpty();

		// Invalid stream -> Refused
		PromotionDecision artsDecision = new PromotionDecision(510L, Outcome.PROMOTED, std11.id(), div11A.id(), 1L, "Arts");
		assertThat(PromotionRules.validateDecision(std10, std10Student, artsDecision, allStandards, allDivisions))
				.anyMatch(e -> e.contains("must be Science or Commerce"));

		// Std 1 -> Std 2: usesStream is false, stream must be null
		StudentCandidate std1Student = new StudentCandidate(501L, 101L, "GR-1", "First Grader", div1A.id(), div1A.name(), 1L, null);
		PromotionDecision streamOnStd2Decision = new PromotionDecision(501L, Outcome.PROMOTED, std2.id(), div2A.id(), 1L, "Science");
		assertThat(PromotionRules.validateDecision(std1, std1Student, streamOnStd2Decision, allStandards, allDivisions))
				.anyMatch(e -> e.contains("Standards without stream cannot have a stream set"));
	}

	@Test
	@DisplayName("FR-012: Promoting Std 11 to 12 keeps old stream unless changed")
	void std11To12KeepsOldStream() {
		StudentCandidate candidate = new StudentCandidate(511L, 111L, "GR-11", "Eleventh Grader", div11A.id(), div11A.name(), 1L, "Science");
		PromotionDefault def = PromotionRules.computeDefault(std11, candidate, allStandards, allDivisions);

		assertThat(def.defaultStream()).isEqualTo("Science");
		assertThat(def.targetStandardId()).isEqualTo(std12.id());

		// Kept stream is valid
		PromotionDecision keptStream = new PromotionDecision(511L, Outcome.PROMOTED, std12.id(), div12A.id(), 1L, def.defaultStream());
		assertThat(PromotionRules.validateDecision(std11, candidate, keptStream, allStandards, allDivisions)).isEmpty();

		// Changed stream to Commerce is also valid
		PromotionDecision changedStream = new PromotionDecision(511L, Outcome.PROMOTED, std12.id(), div12A.id(), 1L, "Commerce");
		assertThat(PromotionRules.validateDecision(std11, candidate, changedStream, allStandards, allDivisions)).isEmpty();
	}

	@Test
	@DisplayName("FR-008: Detained student must stay in same standard")
	void detainedMustStayInSameStandard() {
		StudentCandidate candidate = new StudentCandidate(501L, 101L, "GR-1", "John Doe", div1A.id(), div1A.name(), 1L, null);
		PromotionDecision wrongStdDetained = new PromotionDecision(501L, Outcome.DETAINED, std2.id(), div2A.id(), 1L, null);

		List<String> errors = PromotionRules.validateDecision(std1, candidate, wrongStdDetained, allStandards, allDivisions);
		assertThat(errors).anyMatch(e -> e.contains("must remain in the same standard"));
	}

	@Test
	@DisplayName("FR-008: Division must belong to target standard in the target year")
	void divisionMustBelongToTargetStandard() {
		StudentCandidate candidate = new StudentCandidate(501L, 101L, "GR-1", "John Doe", div1A.id(), div1A.name(), 1L, null);
		// div11A belongs to Standard 11, not Standard 2
		PromotionDecision mismatchedDiv = new PromotionDecision(501L, Outcome.PROMOTED, std2.id(), div11A.id(), 1L, null);

		List<String> errors = PromotionRules.validateDecision(std1, candidate, mismatchedDiv, allStandards, allDivisions);
		assertThat(errors).anyMatch(e -> e.contains("Target division does not belong to the target standard"));
	}

}

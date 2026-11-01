package com.admin.management.yearchange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure domain rules for year-change promotions (FR-006, FR-008, FR-009, FR-010, FR-012).
 * Database-free and side-effect-free.
 */
public final class PromotionRules {

	private static final Set<String> ALLOWED_STREAMS = Set.of("Science", "Commerce");

	private PromotionRules() {
	}

	public enum Outcome {
		PROMOTED, DETAINED, LEFT;

		public static Optional<Outcome> fromString(String value) {
			if (value == null) {
				return Optional.empty();
			}
			try {
				return Optional.of(Outcome.valueOf(value.trim().toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException e) {
				return Optional.empty();
			}
		}
	}

	public record StandardInfo(long id, String name, int displayOrder, boolean usesStream, boolean active) {
	}

	public record DivisionInfo(long id, String name, long standardId) {
	}

	public record StudentCandidate(long enrollmentId, long studentId, String grNo, String studentName,
			long divisionId, String divisionName, long categoryId, String stream) {
	}

	public record PromotionDecision(long enrollmentId, Outcome outcome, Long targetStandardId,
			Long targetDivisionId, Long targetCategoryId, String stream) {
	}

	public record PromotionDefault(Outcome defaultOutcome, Long targetStandardId, Long targetDivisionId,
			Long targetCategoryId, String defaultStream) {
	}

	/**
	 * Resolves the next standard: the active standard with the lowest display order strictly greater
	 * than the current standard's display order (FR-008). Empty when the current standard is the last standard.
	 */
	public static Optional<StandardInfo> nextStandard(StandardInfo current, List<StandardInfo> activeStandards) {
		if (current == null || activeStandards == null) {
			return Optional.empty();
		}
		return activeStandards.stream()
				.filter(StandardInfo::active)
				.filter(s -> s.displayOrder() > current.displayOrder())
				.min(Comparator.comparingInt(StandardInfo::displayOrder));
	}

	/**
	 * Returns true when there is no active standard with a higher display order (FR-009).
	 */
	public static boolean isLastStandard(StandardInfo current, List<StandardInfo> activeStandards) {
		return nextStandard(current, activeStandards).isEmpty();
	}

	/**
	 * Matches a division in the target standard with the same letter/name as the source division (FR-008).
	 * Falls back to matching the trailing letter (e.g. "10-A" -> "A"), or first available division if no match.
	 */
	public static Optional<DivisionInfo> defaultDivision(String sourceDivisionName, List<DivisionInfo> targetStandardDivisions) {
		if (targetStandardDivisions == null || targetStandardDivisions.isEmpty()) {
			return Optional.empty();
		}
		if (sourceDivisionName == null || sourceDivisionName.isBlank()) {
			return Optional.of(targetStandardDivisions.get(0));
		}
		String sourceTrimmed = sourceDivisionName.trim();

		// Exact match (case-insensitive)
		for (DivisionInfo div : targetStandardDivisions) {
			if (div.name().trim().equalsIgnoreCase(sourceTrimmed)) {
				return Optional.of(div);
			}
		}

		// Trailing letter match (e.g. "Std 10 A" or "10-A" matching "A")
		String letter = extractLetter(sourceTrimmed);
		if (!letter.isEmpty()) {
			for (DivisionInfo div : targetStandardDivisions) {
				if (div.name().trim().equalsIgnoreCase(letter)) {
					return Optional.of(div);
				}
			}
		}

		return Optional.of(targetStandardDivisions.get(0));
	}

	private static String extractLetter(String name) {
		for (int i = name.length() - 1; i >= 0; i--) {
			char c = name.charAt(i);
			if (Character.isLetter(c)) {
				return String.valueOf(c);
			}
		}
		return "";
	}

	/**
	 * Computes the default promotion targets and outcomes for a student candidate (FR-008, FR-009, FR-012).
	 */
	public static PromotionDefault computeDefault(StandardInfo currentStandard, StudentCandidate candidate,
			List<StandardInfo> activeStandards, List<DivisionInfo> newYearDivisions) {
		boolean isLast = isLastStandard(currentStandard, activeStandards);
		if (isLast) {
			// FR-009: Std 12 has no promote tick. Default is Detained (same standard and division).
			List<DivisionInfo> sameStdDivisions = newYearDivisions == null ? List.of()
					: newYearDivisions.stream().filter(d -> d.standardId() == currentStandard.id()).toList();
			Long divisionId = defaultDivision(candidate.divisionName(), sameStdDivisions).map(DivisionInfo::id).orElse(null);
			String stream = currentStandard.usesStream() ? candidate.stream() : null;
			return new PromotionDefault(Outcome.DETAINED, currentStandard.id(), divisionId, candidate.categoryId(), stream);
		} else {
			// FR-008: Promoted to next standard with same division letter.
			StandardInfo next = nextStandard(currentStandard, activeStandards).orElseThrow();
			List<DivisionInfo> nextStdDivisions = newYearDivisions == null ? List.of()
					: newYearDivisions.stream().filter(d -> d.standardId() == next.id()).toList();
			Long divisionId = defaultDivision(candidate.divisionName(), nextStdDivisions).map(DivisionInfo::id).orElse(null);
			// FR-012: Std 11->12 keeps stream; Std 10->11 requires stream; Std 1..10 has none.
			String stream = next.usesStream() ? candidate.stream() : null;
			return new PromotionDefault(Outcome.PROMOTED, next.id(), divisionId, candidate.categoryId(), stream);
		}
	}

	/**
	 * Validates a promotion decision for a candidate (FR-006, FR-008, FR-009, FR-010, FR-012).
	 * Returns a list of validation error messages, or an empty list if valid.
	 */
	public static List<String> validateDecision(StandardInfo currentStandard, StudentCandidate candidate,
			PromotionDecision decision, List<StandardInfo> activeStandards, List<DivisionInfo> targetYearDivisions) {
		List<String> errors = new ArrayList<>();

		if (decision == null || decision.outcome() == null) {
			errors.add("Outcome is required for student " + candidate.studentName() + " (" + candidate.grNo() + ").");
			return errors;
		}

		boolean isLast = isLastStandard(currentStandard, activeStandards);

		// FR-009: Last standard cannot be promoted
		if (isLast && decision.outcome() == Outcome.PROMOTED) {
			errors.add("Student " + candidate.studentName() + " (" + candidate.grNo()
					+ ") is in the final standard and cannot be promoted; choose Detained or Left.");
			return errors;
		}

		// FR-010: Outcome LEFT needs no target
		if (decision.outcome() == Outcome.LEFT) {
			return errors;
		}

		// Outcome PROMOTED or DETAINED: target standard check
		Long targetStdId = decision.targetStandardId();
		if (targetStdId == null) {
			errors.add("Target standard is required for " + decision.outcome() + " student " + candidate.studentName() + ".");
			return errors;
		}

		if (decision.outcome() == Outcome.PROMOTED) {
			StandardInfo expectedNext = nextStandard(currentStandard, activeStandards).orElse(null);
			if (expectedNext == null || expectedNext.id() != targetStdId) {
				errors.add("Promoted student " + candidate.studentName() + " must be placed in the next standard ("
						+ (expectedNext != null ? expectedNext.name() : "none") + ").");
			}
		} else if (decision.outcome() == Outcome.DETAINED) {
			if (currentStandard.id() != targetStdId) {
				errors.add("Detained student " + candidate.studentName() + " must remain in the same standard ("
						+ currentStandard.name() + ").");
			}
		}

		// Find target standard info
		StandardInfo targetStandard = activeStandards == null ? null : activeStandards.stream()
				.filter(s -> s.id() == targetStdId)
				.findFirst()
				.orElse(null);

		// Target division check
		Long targetDivId = decision.targetDivisionId();
		if (targetDivId == null) {
			errors.add("Target division is required for student " + candidate.studentName() + ".");
		} else {
			boolean divBelongs = targetYearDivisions != null && targetYearDivisions.stream()
					.anyMatch(d -> d.id() == targetDivId && d.standardId() == targetStdId);
			if (!divBelongs) {
				errors.add("Target division does not belong to the target standard in the new academic year for student "
						+ candidate.studentName() + ".");
			}
		}

		// Target category check
		if (decision.targetCategoryId() == null) {
			errors.add("Category is required for student " + candidate.studentName() + ".");
		}

		// FR-012: Stream check
		if (targetStandard != null) {
			if (targetStandard.usesStream()) {
				if (decision.stream() == null || decision.stream().isBlank()) {
					errors.add("Stream (Science or Commerce) is required for student " + candidate.studentName()
							+ " in " + targetStandard.name() + ".");
				} else if (!ALLOWED_STREAMS.contains(decision.stream())) {
					errors.add("Invalid stream '" + decision.stream() + "' for student " + candidate.studentName()
							+ "; must be Science or Commerce.");
				}
			} else {
				if (decision.stream() != null && !decision.stream().isBlank()) {
					errors.add("Standards without stream cannot have a stream set (student " + candidate.studentName() + ").");
				}
			}
		}

		return errors;
	}

}

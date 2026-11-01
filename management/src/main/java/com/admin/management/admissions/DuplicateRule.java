package com.admin.management.admissions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure duplicate detection rule and import key calculation (FR-031, FR-032).
 *
 * <p>A row is flagged as a possible duplicate when either:
 * <ul>
 *   <li>Rule (a): same first, middle, and last name (case-insensitive, trimmed)</li>
 *   <li>Rule (b): same first name, last name, date of birth, and mobile 1</li>
 * </ul>
 * against existing students (any status) or other waiting entries (Pending or Approved).
 * The flag only highlights and never blocks approval.
 */
public final class DuplicateRule {

	private static final Pattern NON_DIGITS = Pattern.compile("\\D");

	private DuplicateRule() {
	}

	public record PersonCandidate(Long id, String surname, String firstName, String middleName,
			LocalDate dob, String mobile1) {
	}

	public record DuplicateResult(boolean duplicateFlag, List<Long> studentIds, List<Long> requestIds) {
		public static DuplicateResult none() {
			return new DuplicateResult(false, List.of(), List.of());
		}
	}

	/**
	 * Computes the unique import key for a row: submission time + lower-case names + date of birth (FR-031).
	 */
	public static String computeImportKey(String submissionTime, String surname, String firstName,
			String middleName, LocalDate dob) {
		String sub = submissionTime == null ? "" : submissionTime.trim();
		String sur = surname == null ? "" : surname.trim().toLowerCase();
		String first = firstName == null ? "" : firstName.trim().toLowerCase();
		String mid = middleName == null ? "" : middleName.trim().toLowerCase();
		String dobStr = dob == null ? "" : dob.toString();
		return sub + "|" + sur + "|" + first + "|" + mid + "|" + dobStr;
	}

	/**
	 * Rule (a): same first, middle, and last name (case-insensitive, trimmed).
	 */
	public static boolean matchesRuleA(PersonCandidate a, PersonCandidate b) {
		if (a == null || b == null) {
			return false;
		}
		if (!sameText(a.firstName(), b.firstName()) || !sameText(a.surname(), b.surname())) {
			return false;
		}
		return sameTextOrBothBlank(a.middleName(), b.middleName());
	}

	/**
	 * Rule (b): same names (first and last), date of birth, and mobile 1.
	 */
	public static boolean matchesRuleB(PersonCandidate a, PersonCandidate b) {
		if (a == null || b == null) {
			return false;
		}
		if (!sameText(a.firstName(), b.firstName()) || !sameText(a.surname(), b.surname())) {
			return false;
		}
		if (a.dob() == null || b.dob() == null || !a.dob().equals(b.dob())) {
			return false;
		}
		String m1 = cleanMobile(a.mobile1());
		String m2 = cleanMobile(b.mobile1());
		if (m1 == null || m2 == null || !m1.equals(m2)) {
			return false;
		}
		return true;
	}

	/**
	 * Whether two person records are duplicates according to rule (a) or rule (b).
	 */
	public static boolean isDuplicate(PersonCandidate a, PersonCandidate b) {
		return matchesRuleA(a, b) || matchesRuleB(a, b);
	}

	/**
	 * Evaluates candidate duplicates against existing students and waiting requests.
	 */
	public static DuplicateResult checkDuplicates(PersonCandidate candidate,
			Collection<PersonCandidate> students, Collection<PersonCandidate> requests) {
		if (candidate == null) {
			return DuplicateResult.none();
		}

		List<Long> matchedStudentIds = new ArrayList<>();
		if (students != null) {
			for (PersonCandidate s : students) {
				if (s.id() != null && !s.id().equals(candidate.id()) && isDuplicate(candidate, s)) {
					matchedStudentIds.add(s.id());
				}
			}
		}

		List<Long> matchedRequestIds = new ArrayList<>();
		if (requests != null) {
			for (PersonCandidate r : requests) {
				if (r.id() != null && !r.id().equals(candidate.id()) && isDuplicate(candidate, r)) {
					matchedRequestIds.add(r.id());
				}
			}
		}

		boolean isDup = !matchedStudentIds.isEmpty() || !matchedRequestIds.isEmpty();
		return new DuplicateResult(isDup, Collections.unmodifiableList(matchedStudentIds),
				Collections.unmodifiableList(matchedRequestIds));
	}

	private static boolean sameText(String s1, String s2) {
		String t1 = trimToNull(s1);
		String t2 = trimToNull(s2);
		if (t1 == null && t2 == null) {
			return true;
		}
		if (t1 == null || t2 == null) {
			return false;
		}
		return t1.equalsIgnoreCase(t2);
	}

	private static boolean sameTextOrBothBlank(String s1, String s2) {
		String t1 = trimToNull(s1);
		String t2 = trimToNull(s2);
		if (t1 == null && t2 == null) {
			return true;
		}
		if (t1 == null || t2 == null) {
			return false;
		}
		return t1.equalsIgnoreCase(t2);
	}

	private static String cleanMobile(String mobile) {
		if (mobile == null) {
			return null;
		}
		String digits = NON_DIGITS.matcher(mobile).replaceAll("");
		return digits.length() == 10 ? digits : null;
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String trimmed = s.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}

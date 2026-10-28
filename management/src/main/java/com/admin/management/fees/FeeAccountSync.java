package com.admin.management.fees;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.students.api.EnrollmentLookup;
import com.admin.management.students.api.EnrollmentLookup.ClassRecordRef;

/**
 * Works out the fee account of every class record of a year from the year's rows and writes the accounts that are
 * new or differ (FR-006). Finalise and revise both use it, so the rule stays in {@link FeeSelection} only. The caller
 * has already taken the year's status row for writing, so the rows read here are the final set. Receipts are never
 * touched. Writes go in one JDBC batch of upserts (measured in task T003: 2,000 accounts in under 0.1 s).
 */
@Component
class FeeAccountSync {

	private static final String UPSERT = """
			insert into fee_accounts (enrollment_id, academic_year_id, amount, registration_fee, due_date, updated_at)
			values (?, ?, ?, ?, ?, ?)
			on conflict (enrollment_id) do update set amount = excluded.amount,
			    registration_fee = excluded.registration_fee, due_date = excluded.due_date,
			    updated_at = excluded.updated_at
			""";

	private final EnrollmentLookup enrollments;

	private final FeeRowRepository rows;

	private final FeeAccountRepository accounts;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	FeeAccountSync(EnrollmentLookup enrollments, FeeRowRepository rows, FeeAccountRepository accounts,
			JdbcTemplate jdbc, Clock clock) {
		this.enrollments = enrollments;
		this.rows = rows;
		this.accounts = accounts;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/**
	 * Looks at every class record of the year (Left students included) and writes the account of each one whose
	 * account is missing or holds other values. Returns how many records it looked at and how many accounts it wrote.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	FeesAudit.Recalculated recalculate(Long yearId) {
		FeeSelection selection = FeeSelection.of(rows.findByAcademicYearIdOrderById(yearId));
		List<ClassRecordRef> records = enrollments.classRecordsOfYear(yearId);
		Map<Long, FeeAccount> existing = accounts.findByAcademicYearId(yearId)
			.stream()
			.collect(Collectors.toMap(FeeAccount::getEnrollmentId, Function.identity()));
		Instant now = clock.instant();
		List<Object[]> changed = new ArrayList<>();
		for (ClassRecordRef record : records) {
			FeeSelection.Result fee = selection.select(record.standardId(), record.categoryId());
			FeeAccount account = existing.get(record.enrollmentId());
			if (account == null || differs(account, fee)) {
				changed.add(new Object[] { record.enrollmentId(), yearId, Money.scale2(fee.amount()),
						Money.scale2(fee.registrationFee()),
						fee.dueDate() == null ? null : Date.valueOf(fee.dueDate()), java.sql.Timestamp.from(now) });
			}
		}
		if (!changed.isEmpty()) {
			jdbc.batchUpdate(UPSERT, changed);
		}
		return new FeesAudit.Recalculated(records.size(), changed.size());
	}

	private static boolean differs(FeeAccount account, FeeSelection.Result fee) {
		return !sameMoney(account.getAmount(), fee.amount())
				|| !sameMoney(account.getRegistrationFee(), fee.registrationFee())
				|| !Objects.equals(account.getDueDate(), fee.dueDate());
	}

	private static boolean sameMoney(BigDecimal a, BigDecimal b) {
		return a == null ? b == null : b != null && a.compareTo(b) == 0;
	}

}

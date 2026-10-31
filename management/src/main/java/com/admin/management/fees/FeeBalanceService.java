package com.admin.management.fees;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.masterdata.api.MasterDataLookup.YearDates;

/**
 * Reads a student's figures from the views of V8 (research R2) and builds the {@link FeeBalance} and the balance
 * token (R3). Nothing is cached or stored, so the balance always follows the valid receipts.
 */
@Service
class FeeBalanceService {

	private final JdbcTemplate jdbc;

	private final MasterDataLookup masterData;

	private final FeeAccountRepository accounts;

	private final BroughtForwardRepository broughtForwards;

	FeeBalanceService(JdbcTemplate jdbc, MasterDataLookup masterData, FeeAccountRepository accounts,
			BroughtForwardRepository broughtForwards) {
		this.jdbc = jdbc;
		this.masterData = masterData;
		this.accounts = accounts;
		this.broughtForwards = broughtForwards;
	}

	/**
	 * Takes the student's lock until the end of the transaction (R3). Every write that depends on the student's
	 * balance calls this first, so two simultaneous saves for one student run one after the other.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	void lockStudent(Long studentId) {
		jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended('student-fee:' || ?::text, 0))", studentId);
	}

	/** The figures of one class record ({@code enrollmentId} may be null) and the student's earlier years. */
	@Transactional(readOnly = true)
	FeeBalance.Position position(Long studentId, Long enrollmentId) {
		BigDecimal broughtForward = BigDecimal.ZERO;
		BigDecimal paidPrevious = BigDecimal.ZERO;
		List<BigDecimal[]> earlier = jdbc.query("select amount, paid from v_earlier_pending where student_id = ?",
				(rs, n) -> new BigDecimal[] { rs.getBigDecimal("amount"), rs.getBigDecimal("paid") }, studentId);
		if (!earlier.isEmpty()) {
			broughtForward = earlier.get(0)[0];
			paidPrevious = earlier.get(0)[1];
		}
		if (enrollmentId == null) {
			return noFee(broughtForward, paidPrevious);
		}
		List<FeeBalance.Position> rows = jdbc.query("""
				select p.year_fee, p.registration_fee, p.paid_year_fee, p.paid_registration, p.academic_year_id,
				       a.due_date
				from v_fee_position p join fee_accounts a on a.enrollment_id = p.enrollment_id
				where p.enrollment_id = ?
				""", (rs, n) -> {
			BigDecimal yearFee = rs.getBigDecimal("year_fee");
			Date due = rs.getDate("due_date");
			LocalDate dueDate = due == null ? null : due.toLocalDate();
			if (yearFee == null) {
				return new FeeBalance.Position(false, null, null, dueDate, null, null, rs.getBigDecimal("paid_year_fee"),
						rs.getBigDecimal("paid_registration"), BigDecimal.ZERO, BigDecimal.ZERO);
			}
			YearDates year = masterData.yearDates(rs.getLong("academic_year_id")).orElseThrow();
			return new FeeBalance.Position(true, yearFee, rs.getBigDecimal("registration_fee"), dueDate,
					year.startDate(), year.endDate(), rs.getBigDecimal("paid_year_fee"),
					rs.getBigDecimal("paid_registration"), BigDecimal.ZERO, BigDecimal.ZERO);
		}, enrollmentId);
		if (rows.isEmpty()) {
			return noFee(broughtForward, paidPrevious);
		}
		FeeBalance.Position p = rows.get(0);
		return new FeeBalance.Position(p.feeSet(), p.yearFee(), p.registrationFee(), p.dueDate(), p.yearStart(),
				p.yearEnd(), p.paidYearFee(), p.paidRegistration(), broughtForward, paidPrevious);
	}

	@Transactional(readOnly = true)
	FeeBalance balance(Long studentId, Long enrollmentId) {
		return FeeBalance.of(position(studentId, enrollmentId));
	}

	/**
	 * The balance token: {@code r<receipts>.m<marks>.a<account updated_at in microseconds, 0 if none>.b<row version + 1,
	 * 0 if none>} (plus one so a first brought-forward row differs from none). It is not money; it changes whenever a receipt, a mark, a revise of the account or a
	 * brought-forward edit changes what the student owes.
	 */
	@Transactional(readOnly = true)
	String token(Long studentId, Long enrollmentId) {
		long receipts = jdbc.queryForObject("select count(*) from receipts where student_id = ?", Long.class, studentId);
		long marks = jdbc.queryForObject("""
				select count(*) from receipt_marks m join receipts r on r.serial_no = m.receipt_serial_no
				where r.student_id = ?
				""", Long.class, studentId);
		long account = enrollmentId == null ? 0
				: accounts.findById(enrollmentId).map(FeeAccount::getUpdatedAt).map(FeeBalanceService::micros).orElse(0L);
		long broughtForward = broughtForwards.findById(studentId).map(b -> b.getVersion() + 1).orElse(0L);
		return "r" + receipts + ".m" + marks + ".a" + account + ".b" + broughtForward;
	}

	private static long micros(Instant at) {
		return at.getEpochSecond() * 1_000_000L + at.getNano() / 1_000L;
	}

	private static FeeBalance.Position noFee(BigDecimal broughtForward, BigDecimal paidPrevious) {
		return new FeeBalance.Position(false, null, null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
				broughtForward, paidPrevious);
	}

}

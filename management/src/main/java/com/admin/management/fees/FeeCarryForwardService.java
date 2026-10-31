package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.fees.api.FeeCarryForward;

/**
 * Carries forward unpaid year fees of an enrollment into the student's brought-forward dues (FR-020, FR-021, FR-022, FR-024).
 * Registration fee is never carried forward. Writes nothing if unpaid year fee is 0. Locks the student like a receipt.
 */
@Service
public class FeeCarryForwardService implements FeeCarryForward {

	private static final BigDecimal ZERO = new BigDecimal("0.00");

	private final JdbcTemplate jdbc;

	private final FeeBalanceService balances;

	private final BroughtForwardRepository repository;

	private final FeesAudit audit;

	private final Clock clock;

	public FeeCarryForwardService(JdbcTemplate jdbc, FeeBalanceService balances, BroughtForwardRepository repository,
			FeesAudit audit, Clock clock) {
		this.jdbc = jdbc;
		this.balances = balances;
		this.repository = repository;
		this.audit = audit;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public BigDecimal preview(long enrollmentId) {
		List<BigDecimal> list = jdbc.query("select pending_year_fee from v_fee_position where enrollment_id = ?",
				(rs, rowNum) -> rs.getBigDecimal("pending_year_fee"), enrollmentId);
		if (list.isEmpty() || list.get(0) == null) {
			return ZERO;
		}
		return Money.scale2(list.get(0));
	}

	@Override
	@Transactional
	public CarryForwardResult carryForward(long studentId, long oldYearId, long newYearId, long fromEnrollmentId) {
		balances.lockStudent(studentId);

		BigDecimal carriedAmount = preview(fromEnrollmentId);

		Optional<BroughtForward> existing = repository.findById(studentId);
		BigDecimal oldAmount = existing.map(BroughtForward::getAmount).map(Money::scale2).orElse(ZERO);

		if (carriedAmount.compareTo(BigDecimal.ZERO) <= 0) {
			return new CarryForwardResult(ZERO, oldAmount, oldAmount);
		}

		BigDecimal newAmount = Money.scale2(oldAmount.add(carriedAmount));

		if (existing.isPresent()) {
			BroughtForward row = existing.get();
			row.change(newAmount, row.getNote(), clock.instant());
			repository.saveAndFlush(row);
		}
		else {
			int inserted = repository.insertIfAbsent(studentId, newAmount, null, clock.instant());
			if (inserted == 0) {
				BroughtForward row = repository.findById(studentId).orElseThrow();
				row.change(newAmount, row.getNote(), clock.instant());
				repository.saveAndFlush(row);
			}
		}

		audit.carryForwardRecorded(studentId, oldYearId, newYearId, oldAmount, carriedAmount, newAmount);

		return new CarryForwardResult(carriedAmount, oldAmount, newAmount);
	}

}

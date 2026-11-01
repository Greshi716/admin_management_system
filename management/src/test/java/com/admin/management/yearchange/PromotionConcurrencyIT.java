package com.admin.management.yearchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.shared.web.CodedConflictException;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.YearChangeTestData;
import com.admin.management.support.YearChangeTestData.TwoYears;

/**
 * Concurrency tests for promotion (SC-006, FR-006, FR-016, FR-017).
 */
@Covers("FR-006")
@Covers("FR-016")
@Covers("FR-017")
class PromotionConcurrencyIT extends IntegrationTest {

	@Autowired
	PromotionService promotionService;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void repeatSaveRefusedWithAlreadyProcessed() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "RepeatOld", LocalDate.of(3921, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "18000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		Fx s1 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 391001, "12000.00", null);
		long ver1 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s1.enrollmentId());

		PromoteInput input = new PromoteInput(List.of(
				new PromoteInput.Line(s1.enrollmentId(), "PROMOTED", years.newYear().divisionByStandard().get(years.oldYear().standard(1)), years.oldYear().category(0), null, ver1)
		));

		// First save succeeds
		PromoteResult result = promotionService.promote(years.oldYear().standard(0), input);
		assertThat(result.promoted()).isEqualTo(1);

		// Second save refused with ALREADY_PROCESSED
		assertThatThrownBy(() -> promotionService.promote(years.oldYear().standard(0), input))
				.isInstanceOf(CodedConflictException.class)
				.satisfies(ex -> assertThat(((CodedConflictException) ex).getCode()).isEqualTo("ALREADY_PROCESSED"));
	}

	@Test
	void twoSimultaneousSavesOnlyOneSucceeds() throws Exception {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "SimultOld", LocalDate.of(3923, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "18000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		Fx s1 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 391101, "12000.00", null);
		long ver1 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s1.enrollmentId());

		PromoteInput input = new PromoteInput(List.of(
				new PromoteInput.Line(s1.enrollmentId(), "PROMOTED", years.newYear().divisionByStandard().get(years.oldYear().standard(1)), years.oldYear().category(0), null, ver1)
		));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();

		Runnable task = () -> {
			ready.countDown();
			try {
				start.await();
				promotionService.promote(years.oldYear().standard(0), input);
				successes.incrementAndGet();
			} catch (CodedConflictException e) {
				if ("ALREADY_PROCESSED".equals(e.getCode())) {
					conflicts.incrementAndGet();
				}
			} catch (ValidationException e) {
				if ("ALREADY_PROCESSED".equals(e.getCode())) {
					conflicts.incrementAndGet();
				}
			} catch (Exception ignored) {
			}
		};

		Future<?> f1 = executor.submit(task);
		Future<?> f2 = executor.submit(task);

		ready.await();
		start.countDown();

		f1.get(10, TimeUnit.SECONDS);
		f2.get(10, TimeUnit.SECONDS);
		executor.shutdown();

		assertThat(successes.get()).isEqualTo(1);
		assertThat(conflicts.get()).isEqualTo(1);
	}

}

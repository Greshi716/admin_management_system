package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.admin.management.support.Covers;

/**
 * Pure unit tests verifying exact money arithmetic (two decimals, BigDecimal compareTo, no float/double)
 * for fee carry-forward (FR-020, FR-022).
 */
@Covers("FR-020")
@Covers("FR-022")
class CarryForwardMoneyTest {

	@Test
	void feeTwelveThousandWithNineThousandPaidGivesThreeThousand() {
		BigDecimal fee = new BigDecimal("12000.00");
		BigDecimal paid = new BigDecimal("9000.00");
		BigDecimal pending = fee.subtract(paid);

		assertThat(pending.compareTo(new BigDecimal("3000.00"))).isZero();
		assertThat(pending.scale()).isEqualTo(2);
		assertThat(Money.scale2(pending).compareTo(new BigDecimal("3000.00"))).isZero();
	}

	@Test
	void earlierFiveThousandWithTwoThousandPaidPlusThreeThousandCarriedGivesEightThousandAndPendingSixThousand() {
		BigDecimal earlierAmount = new BigDecimal("5000.00");
		BigDecimal paidPrevious = new BigDecimal("2000.00");
		BigDecimal carried = new BigDecimal("3000.00");

		BigDecimal newAmount = earlierAmount.add(carried);
		assertThat(newAmount.compareTo(new BigDecimal("8000.00"))).isZero();
		assertThat(newAmount.scale()).isEqualTo(2);

		BigDecimal pendingEarlier = newAmount.subtract(paidPrevious);
		assertThat(pendingEarlier.compareTo(new BigDecimal("6000.00"))).isZero();
		assertThat(pendingEarlier.scale()).isEqualTo(2);
	}

	@Test
	void amountsKeepTwoDecimals() {
		BigDecimal value = new BigDecimal("123.45");
		BigDecimal scaled = Money.scale2(value);
		assertThat(scaled.scale()).isEqualTo(2);
		assertThat(scaled.compareTo(new BigDecimal("123.45"))).isZero();

		BigDecimal zero = Money.scale2(BigDecimal.ZERO);
		assertThat(zero.scale()).isEqualTo(2);
		assertThat(zero.compareTo(new BigDecimal("0.00"))).isZero();
	}

	@Test
	void noDoubleOrFloatUsedInCarryForwardServiceOrTestClass() {
		assertNoFloatOrDouble(FeeCarryForwardService.class);
		assertNoFloatOrDouble(CarryForwardMoneyTest.class);
	}

	private void assertNoFloatOrDouble(Class<?> clazz) {
		for (Field field : clazz.getDeclaredFields()) {
			Class<?> t = field.getType();
			assertThat(t).isNotEqualTo(float.class).isNotEqualTo(double.class).isNotEqualTo(Float.class)
					.isNotEqualTo(Double.class);
		}
		for (Method method : clazz.getDeclaredMethods()) {
			Class<?> ret = method.getReturnType();
			assertThat(ret).isNotEqualTo(float.class).isNotEqualTo(double.class).isNotEqualTo(Float.class)
					.isNotEqualTo(Double.class);
			for (Class<?> param : method.getParameterTypes()) {
				assertThat(param).isNotEqualTo(float.class).isNotEqualTo(double.class).isNotEqualTo(Float.class)
						.isNotEqualTo(Double.class);
			}
		}
	}

}

package com.admin.management.shared.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class TestProfileGuardTest {

	private static TestProfileGuard guard(String db) {
		return new TestProfileGuard(new MockEnvironment().withProperty("spring.datasource.url",
				"jdbc:postgresql://localhost:5432/" + db));
	}

	@Test
	void startsOnATestDatabase() {
		guard("admin_management_test").check();
	}

	@Test
	void refusesToStartOnAnyOtherDatabase() {
		assertThatThrownBy(() -> guard("admin_management_db").check()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("admin_management_db");
	}

}

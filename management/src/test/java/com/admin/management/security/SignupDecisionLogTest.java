package com.admin.management.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.admin.management.support.Covers;

/** F6 FR-016: the decision log records that signup amends the one-Admin rule for the demo only. */
@Covers("FR-016")
class SignupDecisionLogTest {

	@Test
	void decisionLogRecordsTheDemoOnlyAmendment() throws Exception {
		String log = Files.readString(Path.of("..", "docs", "decision-log.md"));

		assertThat(log).contains("Demo signup (F6, FR-016)").contains("CAMPUSFLOW_SIGNUP_ENABLED").contains("demo only");
	}

}

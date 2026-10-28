package com.admin.management.fees;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the browser tests start from empty fee tables: fee accounts and brought-forward amounts cannot be deleted
 * through the product, and while they exist the students, class records, standards, categories and years they refer
 * to cannot be cleared (every foreign key is RESTRICT). A test that clears students or master data calls this first.
 * It exists only under the {@code test} profile (never in a real installation), next to the other test-support
 * endpoints.
 */
@RestController
@Profile("test")
@RequestMapping("/api/test-support")
class FeeTestSupportController {

	private final JdbcTemplate jdbc;

	FeeTestSupportController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@PostMapping("/clear-fees")
	@Transactional
	ResponseEntity<Void> clearFees() {
		jdbc.update("DELETE FROM fee_accounts");
		jdbc.update("DELETE FROM fee_brought_forward");
		jdbc.update("DELETE FROM fee_rows");
		jdbc.update("DELETE FROM fee_year_status");
		return ResponseEntity.noContent().build();
	}

}

package com.admin.management.audit;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/** A later module (here a test-only "Fees" module) records its own area, action and reason with no schema change. */
@Covers("FR-027")
class AuditExtensibilityIT extends IntegrationTest {

	@Autowired
	AuditService audit;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	MockMvc mvc;

	@Test
	void aNewAreaWithTheCancelActionAndAReasonIsListedAndFilterable() throws Exception {
		String entity = "Receipt#" + UUID.randomUUID();
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> audit.record("Fees", "Cancel",
				entity, Map.of("old", Map.of("status", "Final"), "new", Map.of("status", "Cancelled")),
				"Wrong student chosen"));

		SignedInClient api = SignedInClient.of(mvc);
		api.post("/api/audit-log/search",
				"{\"area\":\"Fees\",\"action\":\"Cancel\",\"globalFilter\":\"" + entity + "\"}")
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.rows[0].area").value("Fees"))
			.andExpect(jsonPath("$.rows[0].action").value("Cancel"))
			.andExpect(jsonPath("$.rows[0].reason").value("Wrong student chosen"))
			.andExpect(jsonPath("$.rows[0].changes").value(containsString("Cancelled")));
		api.post("/api/audit-log/search", "{\"area\":\"Payroll\",\"globalFilter\":\"" + entity + "\"}")
			.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void theOtherLaterActionsAreAccepted() throws Exception {
		SignedInClient api = SignedInClient.of(mvc);
		for (String action : new String[] { "Finalise", "Import" }) {
			String entity = action + "#" + UUID.randomUUID();
			new TransactionTemplate(transactionManager)
				.executeWithoutResult(status -> audit.record("Payroll", action, entity, Map.of(), "monthly close"));

			api.post("/api/audit-log/search",
					"{\"area\":\"Payroll\",\"action\":\"" + action + "\",\"globalFilter\":\"" + entity + "\"}")
				.andExpect(jsonPath("$.total").value(1));
		}
	}

}

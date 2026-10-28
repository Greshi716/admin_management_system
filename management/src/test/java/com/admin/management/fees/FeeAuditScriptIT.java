package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;
import com.admin.management.support.TestAdmin;

/**
 * A scripted run of 8 successful fee changes and 7 refused ones: the audit log gets exactly 8 entries, none for the
 * refusals, each with the admin as actor and the entity names of the plan (test plan SC-007, FR-012-N, FR-012-N2,
 * FR-012-F2, X-AUDIT-2).
 */
@Covers("FR-012")
class FeeAuditScriptIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private long structureVersion(Setup setup) {
		return jdbc.queryForObject("select version from fee_year_status where academic_year_id = ?", Long.class,
				setup.yearId());
	}

	private String row(Long structureVersion, Long version, long standardId, String amount) {
		return "{" + (version == null ? "" : "\"version\":" + version + ",") + "\"structureVersion\":" + structureVersion
				+ ",\"standardId\":" + standardId + ",\"categoryId\":null,\"amount\":" + amount + "}";
	}

	@Test
	void everySuccessfulChangeHasOneEntryAndEveryRefusalHasNone() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "AuditScript", LocalDate.of(3781, 4, 1), 2);
		long enrollment = FeeTestData.student(jdbc, setup, 378101, 0, 0);
		long student = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class, enrollment);
		SignedInClient client = SignedInClient.of(mvc);
		String rows = "/api/fee-structures/" + setup.yearId() + "/rows";
		long before = jdbc.queryForObject("select count(*) from audit_log", Long.class);

		// 8 successful changes
		client.post(rows, row(null, null, setup.standard(0), "1000")).andExpect(status().isCreated()); // 1 Create
		long first = jdbc.queryForObject("select id from fee_rows where standard_id = ?", Long.class, setup.standard(0));
		client.put(rows + "/" + first, row(structureVersion(setup), 0L, setup.standard(0), "1200"))
			.andExpect(status().isOk()); // 2 Edit
		client.post(rows, row(structureVersion(setup), null, setup.standard(1), "2000")).andExpect(status().isCreated()); // 3
		long second = jdbc.queryForObject("select id from fee_rows where standard_id = ?", Long.class, setup.standard(1));
		client.delete(rows + "/" + second + "?version=0&structureVersion=" + structureVersion(setup))
			.andExpect(status().isNoContent()); // 4 Delete
		client.post("/api/fee-structures/" + setup.yearId() + "/finalise",
				"{\"version\":" + structureVersion(setup) + "}").andExpect(status().isOk()); // 5 Finalise
		client.put(rows + "/" + first + "?confirm=true", row(structureVersion(setup), 1L, setup.standard(0), "1300"))
			.andExpect(status().isOk()); // 6 Edit with reason Revise
		client.put("/api/students/" + student + "/brought-forward", "{\"version\":null,\"amount\":10}")
			.andExpect(status().isOk()); // 7 Create
		client.put("/api/students/" + student + "/brought-forward", "{\"version\":0,\"amount\":20}")
			.andExpect(status().isOk()); // 8 Edit

		// 7 refusals
		client.post(rows, row(structureVersion(setup), null, setup.standard(1), "500")).andExpect(status().isConflict());
		client.put(rows + "/" + first + "?confirm=true", row(structureVersion(setup) + 9, 2L, setup.standard(0), "1400"))
			.andExpect(status().isConflict());
		client.post(rows + "?confirm=true", row(structureVersion(setup), null, setup.standard(0), "500"))
			.andExpect(status().isConflict());
		client.post(rows + "?confirm=true", row(structureVersion(setup), null, setup.standard(1), "-5"))
			.andExpect(status().isUnprocessableEntity());
		client.put("/api/students/" + student + "/brought-forward", "{\"version\":1,\"amount\":-1}")
			.andExpect(status().isUnprocessableEntity());
		client.put("/api/students/" + student + "/brought-forward", "{\"version\":99,\"amount\":5}")
			.andExpect(status().isConflict());
		long version = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, enrollment);
		client.put("/api/students/" + student + "/enrollments/" + setup.yearId(),
				"{\"version\":" + version + ",\"standardId\":" + setup.standard(1) + ",\"divisionId\":"
						+ setup.divisionByStandard().get(setup.standard(1)) + ",\"categoryId\":" + setup.category(0) + "}")
			.andExpect(status().isConflict());

		assertThat(jdbc.queryForObject("select count(*) from audit_log", Long.class)).isEqualTo(before + 8);
		List<String> entities = jdbc.queryForList(
				"select distinct entity from audit_log where area = 'Fees' order by entity",
				String.class);
		assertThat(entities).contains("BroughtForward#" + student, "FeeRow#" + first, "FeeRow#" + second,
				"FeeStructure#" + setup.yearId());
		assertThat(jdbc.queryForObject("""
				select count(*) from audit_log l join users u on u.id = l.user_id
				where l.area = 'Fees' and u.username = ? and l.entity in (?, ?, ?, ?)
				""", Long.class, TestAdmin.USERNAME, "BroughtForward#" + student, "FeeRow#" + first, "FeeRow#" + second,
				"FeeStructure#" + setup.yearId())).isEqualTo(8);
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_log where area = 'Fees' and reason = 'Revise' and entity = ?", Long.class,
				"FeeRow#" + first)).isEqualTo(1);
	}

}

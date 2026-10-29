package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignIn;

/** A saved receipt is final: no endpoint, no ORM update, no SQL update or delete, and no call of the developer void (FR-009; SC-004). */
@Covers("FR-009")
class ReceiptImmutabilityIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	Fx student;

	long cheque;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "ImmIT", LocalDate.of(3651, 4, 1), 1);
		student = FeeTestData.studentWithAccount(jdbc, setup, 935001, "10000.00", null);
		cheque = FeeTestData.chequeReceipt(jdbc, setup, student, "IM-1", LocalDate.of(3651, 10, 1), "100.00");
	}

	private int statusOf(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
		MvcResult result = mvc.perform(request).andReturn();
		return result.getResponse().getStatus();
	}

	@Test
	void thereIsNoPutPatchOrDeleteOnAReceiptOrOnTheCollection() throws Exception {
		MockHttpSession session = SignIn.asTestAdmin(mvc);
		for (String path : new String[] { "/api/receipts", "/api/receipts/" + cheque }) {
			assertThat(statusOf(put(path).with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
				.content("{}"))).as("PUT " + path).isIn(404, 405);
			assertThat(statusOf(patch(path).with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
				.content("{}"))).as("PATCH " + path).isIn(404, 405);
			assertThat(statusOf(delete(path).with(csrf()).session(session))).as("DELETE " + path).isIn(404, 405);
		}
		assertThat(statusOf(delete("/api/receipts/" + cheque + "/not-cleared").with(csrf()).session(session)))
			.isIn(404, 405);
	}

	@Test
	void aDirectUpdateOrDeleteOfAReceiptOrAMarkIsRefusedByTheDatabase() {
		long markId = FeeTestData.markNotCleared(jdbc, FeeTestData.chequeReceipt(jdbc, setup, student, "IM-2",
				LocalDate.of(3651, 10, 2), "50.00"), "Bounced");

		for (String sql : new String[] { "update receipts set fee_paid = 1 where serial_no = " + cheque,
				"update receipts set student_name = 'X' where serial_no = " + cheque,
				"delete from receipts where serial_no = " + cheque,
				"update receipt_marks set reason = 'X' where id = " + markId,
				"delete from receipt_marks where id = " + markId }) {
			assertThatThrownBy(() -> jdbc.update(sql)).as(sql)
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("append-only");
		}
		assertThatThrownBy(() -> jdbc.execute("truncate receipt_marks")).isInstanceOf(DataAccessException.class);
	}

	@Test
	void everyColumnOfAReceiptIsIdenticalBeforeAndAfterAChequeMark() {
		long serial = FeeTestData.chequeReceipt(jdbc, setup, student, "IM-3", LocalDate.of(3651, 10, 3), "75.00");
		Map<String, Object> before = jdbc.queryForMap("select * from receipts where serial_no = ?", serial);

		FeeTestData.markNotCleared(jdbc, serial, "Bounced");

		Map<String, Object> after = jdbc.queryForMap("select * from receipts where serial_no = ?", serial);
		assertThat(after).isEqualTo(before);
	}

	@Test
	void noProductionSourceCallsTheDeveloperVoid() throws IOException {
		Path sources = Paths.get("src", "main", "java");
		List<Path> hits;
		try (Stream<Path> files = Files.walk(sources)) {
			hits = files.filter(p -> p.toString().endsWith(".java")).filter(p -> {
				try {
					return Files.readString(p).contains("void_receipt");
				}
				catch (IOException ex) {
					throw new IllegalStateException(ex);
				}
			}).toList();
		}
		assertThat(hits).isEmpty();
	}

	@Test
	void theReceiptEntitiesAreImmutableToHibernate() {
		assertThat(Receipt.class.isAnnotationPresent(org.hibernate.annotations.Immutable.class)).isTrue();
		assertThat(ReceiptMark.class.isAnnotationPresent(org.hibernate.annotations.Immutable.class)).isTrue();
	}

}

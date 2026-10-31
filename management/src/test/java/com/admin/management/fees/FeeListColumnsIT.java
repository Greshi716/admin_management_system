package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.JsonMoney;
import com.admin.management.support.SignedInClient;
import com.admin.management.support.StudentTestData;
import com.admin.management.support.StudentTestData.YearSetup;

/** The Total fee and Fee state columns of the Students list: sort, filter, paging, zero fee, shown record (FR-010); the paid and pending columns of F3 end F2's hiding of them. */
@Covers("FR-010")
class FeeListColumnsIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private void finalise(Setup setup) throws Exception {
		SignedInClient.of(mvc).post("/api/fee-structures/" + setup.yearId() + "/finalise", "{\"version\":0}")
			.andExpect(status().isOk());
	}

	private String search(String filter, String extra) {
		return "{\"first\":0,\"rows\":25,\"globalFilter\":\"" + filter + "\"" + (extra.isEmpty() ? "" : "," + extra) + "}";
	}

	@Test
	void sortingByTotalFeeOrdersTheAmountsAndPutsTheBlankOnesLast() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "ListSort", LocalDate.of(3771, 4, 1), 3);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "36000", "500", null);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), setup.category(0), "40000", null, null);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(1), null, "30000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		FeeTestData.student(jdbc, setup, 377101, 0, 0); // A 40,500.00
		FeeTestData.student(jdbc, setup, 377102, 0, 1); // B 36,500.00
		FeeTestData.student(jdbc, setup, 377103, 1, 0); // C 30,000.00
		FeeTestData.student(jdbc, setup, 377104, 1, 0); // D 30,000.00
		FeeTestData.student(jdbc, setup, 377105, 0, 1); // E 36,500.00
		FeeTestData.student(jdbc, setup, 377106, 2, 0); // F: Std 3 has no row
		finalise(setup);
		clock.setDate(LocalDate.of(3771, 10, 1));
		SignedInClient client = SignedInClient.of(mvc);

		client.post("/api/students/search", search("gr-3771", "\"sortField\":\"totalFee\",\"sortOrder\":1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(6))
			.andExpect(JsonMoney.at("$.rows[0].totalFee", "30000.00"))
			.andExpect(JsonMoney.at("$.rows[1].totalFee", "30000.00"))
			.andExpect(JsonMoney.at("$.rows[2].totalFee", "36500.00"))
			.andExpect(JsonMoney.at("$.rows[3].totalFee", "36500.00"))
			.andExpect(JsonMoney.at("$.rows[4].totalFee", "40500.00"))
			.andExpect(jsonPath("$.rows[5].totalFee").doesNotExist())
			.andExpect(jsonPath("$.rows[5].feeState").value("Fee not set"));
		client.post("/api/students/search",
				search("gr-3771", "\"filters\":{\"totalFee\":{\"value\":36500,\"matchMode\":\"equals\"}}"))
			.andExpect(jsonPath("$.rows[*].grNo", containsInAnyOrder("GR-377102", "GR-377105")));
	}

	@Test
	void pagingKeepsTheTotalFeeOfEachRowOnItsOwnPage() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "ListPage", LocalDate.of(3772, 4, 1), 1);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "1000", "50", null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		for (int i = 1; i <= 12; i++) {
			FeeTestData.student(jdbc, setup, 377200 + i, 0, 0);
		}
		finalise(setup);
		clock.setDate(LocalDate.of(3772, 10, 1));
		SignedInClient client = SignedInClient.of(mvc);
		String sort = "\"sortField\":\"grNo\",\"sortOrder\":1";

		client.post("/api/students/search",
				"{\"first\":0,\"rows\":10,\"globalFilter\":\"gr-3772\"," + sort + "}")
			.andExpect(jsonPath("$.total").value(12))
			.andExpect(jsonPath("$.rows", hasSize(10)))
			.andExpect(jsonPath("$.rows[0].grNo").value("GR-377201"))
			.andExpect(jsonPath("$.rows[*].totalFee", everyItem(is(1050.0))));
		client.post("/api/students/search",
				"{\"first\":10,\"rows\":10,\"globalFilter\":\"gr-3772\"," + sort + "}")
			.andExpect(jsonPath("$.total").value(12))
			.andExpect(jsonPath("$.rows", hasSize(2)))
			.andExpect(jsonPath("$.rows[0].grNo").value("GR-377211"))
			.andExpect(jsonPath("$.rows[*].totalFee", everyItem(is(1050.0))));
	}

	@Test
	void aFeeOfZeroIsShownAsZeroNotBlankAndHasNoFeeState() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "ListZero", LocalDate.of(3773, 4, 1), 1);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "0.00", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		FeeTestData.student(jdbc, setup, 377301, 0, 0);
		finalise(setup);
		clock.setDate(LocalDate.of(3773, 10, 1));

		SignedInClient.of(mvc).post("/api/students/search", search("gr-3773", ""))
			.andExpect(JsonMoney.at("$.rows[0].totalFee", "0.00"))
			.andExpect(jsonPath("$.rows[0].feeState").doesNotExist());
	}

	@Test
	void theListUsesTheFeeOfTheSameRecordTheProfileShows() throws Exception {
		Setup older = FeeTestData.year(jdbc, "ListOld", LocalDate.of(3774, 4, 1), 1);
		FeeTestData.addRow(jdbc, older.yearId(), older.standard(0), null, "20000", null, null);
		FeeTestData.setStatus(jdbc, older.yearId(), false);
		long student = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				FeeTestData.student(jdbc, older, 377401, 0, 0));
		finalise(older);
		Setup newer = FeeTestData.year(jdbc, "ListNew", LocalDate.of(3775, 4, 1), 1);
		FeeTestData.addRow(jdbc, newer.yearId(), newer.standard(0), null, "25000", null, null);
		FeeTestData.setStatus(jdbc, newer.yearId(), false);
		StudentTestData.insertEnrollment(jdbc, student, new YearSetup(newer.yearId(), newer.yearStart(),
				newer.standard(0), newer.divisionByStandard().get(newer.standard(0))), newer.category(0));
		finalise(newer);
		clock.setDate(LocalDate.of(3775, 10, 1));
		SignedInClient client = SignedInClient.of(mvc);

		client.post("/api/students/search", search("gr-3774", ""))
			.andExpect(jsonPath("$.rows", hasSize(1)))
			.andExpect(JsonMoney.at("$.rows[0].totalFee", "25000.00"));
		client.get("/api/students/" + student + "/fees")
			.andExpect(content().string(containsString("\"totalFee\":25000.00")));
	}

	@Test
	void thePaidAndPendingColumnsExistAndAStudentWithNoReceiptsIsUnpaid() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "ListNoPaid", LocalDate.of(3776, 4, 1), 1);
		FeeTestData.addRow(jdbc, setup.yearId(), setup.standard(0), null, "1000", null, null);
		FeeTestData.setStatus(jdbc, setup.yearId(), false);
		FeeTestData.student(jdbc, setup, 377601, 0, 0);
		finalise(setup);
		clock.setDate(LocalDate.of(3776, 10, 1));
		SignedInClient client = SignedInClient.of(mvc);

		client.post("/api/students/search", search("gr-3776", "\"sortField\":\"paid\",\"sortOrder\":1"))
			.andExpect(status().isOk());
		client.post("/api/students/search", search("gr-3776", "\"sortField\":\"pending\",\"sortOrder\":1"))
			.andExpect(status().isOk());
		client.post("/api/students/search", search("gr-3776", ""))
			.andExpect(status().isOk())
			.andExpect(JsonMoney.at("$.rows[0].totalFee", "1000.00"))
			.andExpect(JsonMoney.at("$.rows[0].paid", "0.00"))
			.andExpect(JsonMoney.at("$.rows[0].pending", "1000.00"))
			.andExpect(jsonPath("$.rows[0].feeStatus").value("Unpaid"));
	}

}

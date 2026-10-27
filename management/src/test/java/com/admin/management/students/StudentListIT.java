package com.admin.management.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;
import com.admin.management.support.StudentTestData;
import com.admin.management.support.StudentTestData.YearSetup;

/** The Students list and export: scope, search, record shown, filters, sort, paging, masking and audit. */
@Covers("FR-008")
@Covers("FR-010")
@Covers("FR-012")
class StudentListIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient api;

	long categoryOne;

	long categoryTwo;

	long standardOne;

	long standardTwo;

	long divisionB;

	YearSetup current;

	YearSetup earlier;

	/**
	 * GR-1 Testsurname Alpha, S1-A this year; GR-2 Samplesurname Bravo, S2-B this year, Mobile 2 9111111111;
	 * GR-3 Demosurname Charlie, only an earlier year (S1-A); GR-4 Mocksurname Delta, Left, S1-A this year.
	 */
	@BeforeEach
	void setUp() throws Exception {
		clock.setDate(LocalDate.of(2026, 10, 25));
		jdbc.update("delete from fee_accounts");
		jdbc.update("delete from student_photos");
		jdbc.update("delete from student_enrollments");
		jdbc.update("delete from student_gr_numbers");
		jdbc.update("delete from students");
		jdbc.update("delete from divisions");
		jdbc.update("delete from standards");
		jdbc.update("delete from academic_years");
		jdbc.update("update student_categories set active = true");
		long currentYear = year("2026-27", "2026-04-01", "2027-03-31");
		long earlierYear = year("2025-26", "2025-04-01", "2026-03-31");
		standardOne = standard("Standard 1", 1);
		standardTwo = standard("Standard 2", 2);
		long divisionA = division(currentYear, standardOne, "A");
		divisionB = division(currentYear, standardTwo, "B");
		long earlierA = division(earlierYear, standardOne, "A");
		List<Long> categories = jdbc.queryForList("select id from student_categories order by id limit 2", Long.class);
		categoryOne = categories.get(0);
		categoryTwo = categories.get(1);
		current = new YearSetup(currentYear, LocalDate.of(2026, 4, 1), standardOne, divisionA);
		earlier = new YearSetup(earlierYear, LocalDate.of(2025, 4, 1), standardOne, earlierA);

		long s1 = StudentTestData.insertStudent(jdbc,
				StudentTestData.valid(1, categoryOne, standardOne).withName("Testsurname", "Alpha", null));
		StudentTestData.insertEnrollment(jdbc, s1, current, categoryOne);
		long s2 = StudentTestData.insertStudent(jdbc,
				StudentTestData.valid(2, categoryTwo, standardOne).withName("Samplesurname", "Bravo", null));
		StudentTestData.insertEnrollment(jdbc, s2, new YearSetup(currentYear, current.yearStartDate(), standardTwo,
				divisionB), categoryTwo);
		jdbc.update("update students set mobile_2 = '9111111111' where id = ?", s2);
		StudentTestData.insertStudentWithOnlyEarlierYear(jdbc,
				StudentTestData.valid(3, categoryOne, standardOne).withName("Demosurname", "Charlie", null), earlier);
		long s4 = StudentTestData.insertStudent(jdbc,
				StudentTestData.valid(4, categoryOne, standardOne).withName("Mocksurname", "Delta", null));
		StudentTestData.insertEnrollment(jdbc, s4, current, categoryOne);
		jdbc.update("update students set status = 'Left' where id = ?", s4);
		api = SignedInClient.of(mvc);
	}

	private long year(String name, String from, String to) {
		return jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values (?, ?::date, ?::date) returning id",
				Long.class, name, from, to);
	}

	private long standard(String name, int order) {
		return jdbc.queryForObject("insert into standards (name, display_order) values (?, ?) returning id", Long.class,
				name, order);
	}

	private long division(long yearId, long standardId, String name) {
		return jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, ?) returning id", Long.class,
				yearId, standardId, name);
	}

	private String request(String extra) {
		return "{\"first\":0,\"rows\":25" + (extra.isEmpty() ? "" : "," + extra) + "}";
	}

	@Test
	void theListOpensEvenWhenNoStandardDivisionOrYearExistsYet() throws Exception {
		jdbc.update("delete from student_photos");
		jdbc.update("delete from student_enrollments");
		jdbc.update("delete from student_gr_numbers");
		jdbc.update("delete from students");
		jdbc.update("delete from divisions");
		jdbc.update("delete from standards");
		jdbc.update("delete from academic_years");

		api.post("/api/students/search", request("\"sortField\":\"stdDiv\",\"sortOrder\":1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void theDefaultListHidesStudentsWhoLeft() throws Exception {
		api.post("/api/students/search", request("\"sortField\":\"grNo\",\"sortOrder\":1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.rows[*].grNo", contains("GR-1", "GR-2", "GR-3")));
	}

	@Test
	void theStatusScopeChoosesLeftOrAll() throws Exception {
		api.post("/api/students/search", request("\"status\":\"Left\""))
			.andExpect(jsonPath("$.rows[*].grNo", contains("GR-4")));
		api.post("/api/students/search", request("\"status\":\"All\""))
			.andExpect(jsonPath("$.total").value(4));
		api.post("/api/students/search", request("\"status\":\"Active\""))
			.andExpect(jsonPath("$.total").value(3));
		api.post("/api/students/search", request("\"status\":\"Gone\""))
			.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void searchFindsByNameGrNoAndEitherMobile() throws Exception {
		api.post("/api/students/search", request("\"globalFilter\":\"Samplesurname Bravo\""))
			.andExpect(jsonPath("$.rows[*].grNo", contains("GR-2")));
		api.post("/api/students/search", request("\"globalFilter\":\"gr-3\""))
			.andExpect(jsonPath("$.rows[*].grNo", contains("GR-3")));
		api.post("/api/students/search", request("\"globalFilter\":\"9111111111\""))
			.andExpect(jsonPath("$.rows[*].grNo", contains("GR-2")));
		api.post("/api/students/search", request("\"globalFilter\":\"9000000001\""))
			.andExpect(jsonPath("$.rows[*].grNo", contains("GR-1")));
	}

	@Test
	void aMobileNumberTypedWithCountryCodeOrSpacesFindsTheStudent() throws Exception {
		api.post("/api/students/search", request("\"globalFilter\":\"+91 91111 11111\""))
			.andExpect(jsonPath("$.rows[*].grNo", contains("GR-2")));
		api.post("/api/students/search", request("\"globalFilter\":\"09000000001\""))
			.andExpect(jsonPath("$.rows[*].grNo", contains("GR-1")));
	}

	@Test
	void searchNeverMatchesAadhaarOrReligionCaste() throws Exception {
		api.post("/api/students/search", request("\"globalFilter\":\"100000000001\""))
			.andExpect(jsonPath("$.total").value(0));
		api.post("/api/students/search", request("\"globalFilter\":\"Test religion\""))
			.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void theRecordShownIsTheCurrentYearsElseTheMostRecent() throws Exception {
		api.post("/api/students/search", request("\"sortField\":\"grNo\",\"sortOrder\":1"))
			.andExpect(jsonPath("$.rows[0].stdDiv").value("Standard 1-A"))
			.andExpect(jsonPath("$.rows[0].recordYear").value("2026-27"))
			.andExpect(jsonPath("$.rows[1].stdDiv").value("Standard 2-B"))
			.andExpect(jsonPath("$.rows[2].stdDiv").value("Standard 1-A"))
			.andExpect(jsonPath("$.rows[2].recordYear").value("2025-26"))
			.andExpect(jsonPath("$.rows[2].recordYearId").value(earlier.yearId()));
	}

	@Test
	void filtersOnStandardDivisionAndCategoryApplyToTheRecordShown() throws Exception {
		api.post("/api/students/search", filter("standardId", standardTwo))
			.andExpect(jsonPath("$.rows[*].grNo", contains("GR-2")));
		api.post("/api/students/search", filter("divisionId", divisionB))
			.andExpect(jsonPath("$.rows[*].grNo", contains("GR-2")));
		api.post("/api/students/search", filter("categoryId", categoryOne))
			.andExpect(jsonPath("$.rows[*].grNo", containsInAnyOrder("GR-1", "GR-3")));
		api.post("/api/students/search", filter("standardId", standardOne))
			.andExpect(jsonPath("$.rows[*].grNo", containsInAnyOrder("GR-1", "GR-3")));
	}

	private String filter(String field, long id) {
		return request("\"filters\":{\"" + field + "\":{\"value\":" + id + ",\"matchMode\":\"equals\"}}");
	}

	@Test
	void sortsByStdDivAndByCategory() throws Exception {
		api.post("/api/students/search", request("\"sortField\":\"stdDiv\",\"sortOrder\":-1"))
			.andExpect(jsonPath("$.rows[0].grNo").value("GR-2"));
		api.post("/api/students/search", request("\"sortField\":\"category\",\"sortOrder\":1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows", hasSize(3)));
	}

	@Test
	void pagesAndCountsTheWholeMatch() throws Exception {
		api.post("/api/students/search", "{\"first\":0,\"rows\":10,\"sortField\":\"grNo\",\"sortOrder\":1}")
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.rows", hasSize(3)));
		api.post("/api/students/search", "{\"first\":2,\"rows\":10,\"sortField\":\"grNo\",\"sortOrder\":1}")
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.rows", hasSize(1)));
	}

	@Test
	void aadhaarAndReligionCasteAreMaskedAndCannotBeFilteredOrSortedBy() throws Exception {
		api.post("/api/students/search", request("\"sortField\":\"grNo\",\"sortOrder\":1"))
			.andExpect(jsonPath("$.rows[0].aadhaar").value("********0001"));
		api.post("/api/students/search",
				request("\"filters\":{\"aadhaar\":{\"value\":\"100000000001\",\"matchMode\":\"equals\"}}"))
			.andExpect(status().isUnprocessableEntity());
		api.post("/api/students/search", request("\"sortField\":\"aadhaar\",\"sortOrder\":1"))
			.andExpect(status().isUnprocessableEntity());
		api.post("/api/students/search", request("\"sortField\":\"religionCaste\",\"sortOrder\":1"))
			.andExpect(status().isUnprocessableEntity());
		api.post("/api/students/search",
				request("\"filters\":{\"religionCaste\":{\"value\":\"x\",\"matchMode\":\"contains\"}}"))
			.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void exportHasAllMatchingRowsAllColumnsMaskedValuesAndOneAuditEntry() throws Exception {
		int before = exportEntries();

		byte[] file = api.post("/api/students/export", request("\"status\":\"All\""))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
			Sheet sheet = workbook.getSheetAt(0);
			assertThat(sheet.getLastRowNum()).isEqualTo(4);
			List<String> headers = new ArrayList<>();
			for (var cell : sheet.getRow(0)) {
				headers.add(cell.getStringCellValue());
			}
			assertThat(headers).contains("GR No.", "Name", "Std / Div", "Category", "Status", "Surname", "Date of birth",
					"Mobile 1", "Mobile 2", "Address", "Admission date", "Admission standard", "Previous school",
					"Entry source", "Serial no.", "Aadhaar", "Religion / caste");
			assertThat(headers).doesNotContain("Id", "Version");
			Row first = sheet.getRow(1);
			String aadhaar = first.getCell(headers.indexOf("Aadhaar")).getStringCellValue();
			String caste = first.getCell(headers.indexOf("Religion / caste")).getStringCellValue();
			assertThat(aadhaar).startsWith("********");
			assertThat(caste).startsWith("*");
		}
		assertThat(exportEntries()).isEqualTo(before + 1);
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_log where area = 'Students' and action = 'Export' and entity = 'Students'",
				Integer.class)).isEqualTo(before + 1);
	}

	@Test
	void aFailedExportWritesNoAuditEntry() throws Exception {
		int before = exportEntries();

		api.post("/api/students/export", request("\"sortField\":\"aadhaar\",\"sortOrder\":1"))
			.andExpect(status().isUnprocessableEntity());

		assertThat(exportEntries()).isEqualTo(before);
	}


	/** GR-1 has a fee of 28,000.00 plus 500.00; GR-2 has an account without an amount; GR-3 and GR-4 have no account. */
	private void giveAccounts() {
		long first = enrollmentOf("GR-1");
		long second = enrollmentOf("GR-2");
		jdbc.update("""
				insert into fee_accounts (enrollment_id, academic_year_id, amount, registration_fee, due_date, updated_at)
				values (?, ?, 28000.00, 500.00, null, now()), (?, ?, null, null, null, now())
				""", first, current.yearId(), second, current.yearId());
	}

	private long enrollmentOf(String grNo) {
		return jdbc.queryForObject(
				"select e.id from student_enrollments e join students s on s.id = e.student_id where s.gr_no = ? "
						+ "order by e.year_start_date desc limit 1",
				Long.class, grNo);
	}

	@Test
	void theListShowsTheTotalFeeAndTheFeeStateOfTheRecordShown() throws Exception {
		giveAccounts();

		api.post("/api/students/search", request("\"sortField\":\"grNo\",\"sortOrder\":1"))
			.andExpect(jsonPath("$.rows[0].totalFee").value(28500.00))
			.andExpect(jsonPath("$.rows[0].feeState").doesNotExist())
			.andExpect(jsonPath("$.rows[1].totalFee").doesNotExist())
			.andExpect(jsonPath("$.rows[1].feeState").value("NOT_SET"))
			.andExpect(jsonPath("$.rows[2].totalFee").doesNotExist())
			.andExpect(jsonPath("$.rows[2].feeState").doesNotExist());
	}

	@Test
	void theListSortsAndFiltersByTotalFee() throws Exception {
		giveAccounts();

		api.post("/api/students/search", request("\"sortField\":\"totalFee\",\"sortOrder\":-1"))
			.andExpect(status().isOk());
		api.post("/api/students/search",
				request("\"filters\":{\"totalFee\":{\"value\":28500.00,\"matchMode\":\"equals\"}}"))
			.andExpect(jsonPath("$.rows[*].grNo", contains("GR-1")));
		api.post("/api/students/search",
				request("\"filters\":{\"totalFee\":{\"value\":1,\"matchMode\":\"equals\"}}"))
			.andExpect(jsonPath("$.rows", hasSize(0)));
	}

	@Test
	void theExportHasTheTotalFeeAsANumericCellWithTwoDecimalsAndABlankWhereNoFeeIsSet() throws Exception {
		giveAccounts();

		byte[] file = api.post("/api/students/export", request("\"sortField\":\"grNo\",\"sortOrder\":1"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
			Sheet sheet = workbook.getSheetAt(0);
			List<String> headers = new ArrayList<>();
			for (var cell : sheet.getRow(0)) {
				headers.add(cell.getStringCellValue());
			}
			assertThat(headers).contains("Total fee", "Fee state");
			var fee = sheet.getRow(1).getCell(headers.indexOf("Total fee"));
			assertThat(fee.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);
			assertThat(fee.getNumericCellValue()).isEqualTo(28500.0);
			assertThat(fee.getCellStyle().getDataFormatString()).isEqualTo("0.00");
			var notSet = sheet.getRow(2).getCell(headers.indexOf("Total fee"));
			assertThat(notSet == null || notSet.getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK).isTrue();
			assertThat(sheet.getRow(2).getCell(headers.indexOf("Fee state")).getStringCellValue()).isEqualTo("NOT_SET");
		}
	}

	private int exportEntries() {
		return jdbc.queryForObject("select count(*) from audit_log where action = 'Export'", Integer.class);
	}

}

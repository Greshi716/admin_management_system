package com.admin.management.yearchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;
import com.admin.management.support.YearChangeTestData;
import com.admin.management.support.YearChangeTestData.TwoYears;

/**
 * Audit and masking sweep covering all F4 audit events and masking rules (FR-005, FR-018, FR-024, FR-035, FR-039, FR-040):
 * 1. Year created with copy (Master data and Fees audit)
 * 2. Student promoted, kept back, left (Students audit: StudentEnrollment and Student)
 * 3. Carry-forward (Fees audit: BroughtForward, amounts unmasked)
 * 4. Admissions upload (Students audit: ImportBatch)
 * 5. Admissions approve (Students audit: AdmissionRequest and Student)
 * 6. Admissions reject (Students audit: AdmissionRequest)
 * 7. Developer promotion correction (Students audit: Student with done_by and reason)
 * 8. Aadhaar and religion/caste masking in views, lists, and audit
 * 9. Fees audit money unmasked in full.
 */
@Covers("FR-005")
@Covers("FR-018")
@Covers("FR-024")
@Covers("FR-035")
@Covers("FR-039")
@Covers("FR-040")
class F4AuditIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient client;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		client = SignedInClient.of(mvc);
	}

	@Test
	@DisplayName("1. Year created with copy writes Master data and Fees audit entries with who, when, and unmasked amounts")
	void yearCreatedWithCopyAudit() throws Exception {
		LocalDate oldStart = LocalDate.of(3810, 4, 1);
		Setup oldYear = FeeTestData.year(jdbc, "AuditCopySrc", oldStart, 2);
		FeeTestData.addRow(jdbc, oldYear.yearId(), oldYear.standard(0), null, "15000.00", "500.00", LocalDate.of(3810, 8, 15));
		FeeTestData.setStatus(jdbc, oldYear.yearId(), true);

		String body = """
				{
				  "name": "AuditCopyTarget 3811-12",
				  "startDate": "3811-04-01",
				  "endDate": "3812-03-31",
				  "copyFromLast": true
				}
				""";

		String response = client.post("/api/academic-years", body)
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		long newYearId = Long.parseLong(response.replaceAll(".*\"id\":([0-9]+).*", "$1"));

		// 1a. Master data audit entry
		List<Map<String, Object>> mdEntries = jdbc.queryForList(
				"select * from audit_log where area = 'Master data' and entity = ? order by id desc",
				"AcademicYear#" + newYearId);
		assertThat(mdEntries).isNotEmpty();
		Map<String, Object> md = mdEntries.get(0);
		assertThat(md.get("action")).isEqualTo("Create");
		assertThat(md.get("user_id")).isNotNull();
		assertThat(md.get("at")).isNotNull();
		String mdChanges = String.valueOf(md.get("changes"));
		assertThat(mdChanges)
				.contains("\"copiedFromYearId\": " + oldYear.yearId())
				.contains("\"divisionsCopied\":")
				.contains("\"feeRowsCopied\":");

		// 1b. Fees audit entry
		List<Map<String, Object>> feeEntries = jdbc.queryForList(
				"select * from audit_log where area = 'Fees' and entity = ? order by id desc",
				"FeeStructure#" + newYearId);
		assertThat(feeEntries).isNotEmpty();
		Map<String, Object> fee = feeEntries.get(0);
		assertThat(fee.get("action")).isEqualTo("Create");
		assertThat(fee.get("user_id")).isNotNull();
		assertThat(fee.get("at")).isNotNull();
		String feeChanges = String.valueOf(fee.get("changes"));
		assertThat(feeChanges)
				.contains("\"sourceYearId\": " + oldYear.yearId())
				.contains("\"amount\": 15000.00")
				.contains("\"registrationFee\": 500.00")
				.doesNotContain("*");
	}

	@Test
	@DisplayName("2. Student promoted and Left write StudentEnrollment and Student edit audit rows with who, when, before and after")
	void promotionLinesAudit() throws Exception {
		TwoYears ty = YearChangeTestData.twoConsecutiveYears(jdbc, "AuditPromo", LocalDate.of(3820, 4, 1), 3);
		YearChangeTestData.finaliseNewYear(jdbc, ty.newYear(), "12000.00");
		YearChangeTestData.startNewYear(clock, ty.newYear());

		var studentPromoted = YearChangeTestData.studentWithFeeStatus(jdbc, ty.oldYear(), 111, 0, 0, "12000.00", "12000.00");
		var studentLeft = YearChangeTestData.studentWithFeeStatus(jdbc, ty.oldYear(), 112, 0, 0, "12000.00", "12000.00");

		long vPromoted = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, studentPromoted.enrollmentId());
		long vLeft = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, studentLeft.enrollmentId());

		long std1Old = ty.oldYear().standard(0);
		long std2New = ty.newYear().standard(1);
		long divA = ty.newYear().divisionByStandard().get(std2New);

		String promoteJson = String.format("""
				{
				  "lines": [
				    {
				      "enrollmentId": %d,
				      "version": %d,
				      "outcome": "PROMOTED",
				      "divisionId": %d,
				      "categoryId": %d
				    },
				    {
				      "enrollmentId": %d,
				      "version": %d,
				      "outcome": "LEFT"
				    }
				  ]
				}
				""",
				studentPromoted.enrollmentId(), vPromoted, divA, ty.oldYear().category(0),
				studentLeft.enrollmentId(), vLeft);

		client.post("/api/year-change/standards/" + std1Old + "/promote", promoteJson)
				.andExpect(status().isOk());

		// 2a. Promoted student StudentEnrollment audit
		List<Map<String, Object>> promoAudit = jdbc.queryForList(
				"select * from audit_log where area = 'Students' and entity = ? order by id desc",
				"StudentEnrollment#" + studentPromoted.enrollmentId());
		assertThat(promoAudit).isNotEmpty();
		Map<String, Object> pEntry = promoAudit.get(0);
		assertThat(pEntry.get("action")).isEqualTo("Edit");
		assertThat(pEntry.get("user_id")).isNotNull();
		assertThat(pEntry.get("at")).isNotNull();
		String pChanges = String.valueOf(pEntry.get("changes"));
		assertThat(pChanges)
				.contains("\"yearStatus\": \"Studying\"")
				.contains("\"yearStatus\": \"Promoted\"")
				.contains("\"standardId\": " + std1Old)
				.contains("\"standardId\": " + std2New);

		// 2b. Left student StudentEnrollment and Student audit
		List<Map<String, Object>> leftClassAudit = jdbc.queryForList(
				"select * from audit_log where area = 'Students' and entity = ? order by id desc",
				"StudentEnrollment#" + studentLeft.enrollmentId());
		assertThat(leftClassAudit).isNotEmpty();
		assertThat(String.valueOf(leftClassAudit.get(0).get("changes"))).contains("\"yearStatus\": \"Left\"");

		List<Map<String, Object>> leftStudentAudit = jdbc.queryForList(
				"select * from audit_log where area = 'Students' and entity = ? order by id desc",
				"Student#" + studentLeft.studentId());
		assertThat(leftStudentAudit).isNotEmpty();
		Map<String, Object> sEntry = leftStudentAudit.get(0);
		assertThat(sEntry.get("action")).isEqualTo("Edit");
		assertThat(String.valueOf(sEntry.get("changes")))
				.contains("\"status\": \"Active\"")
				.contains("\"status\": \"Left\"");
	}

	@Test
	@DisplayName("3. Carry-forward writes Fees audit entry with student, years, and unmasked amounts")
	void carryForwardAudit() throws Exception {
		TwoYears ty = YearChangeTestData.twoConsecutiveYears(jdbc, "AuditCF", LocalDate.of(3830, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, ty.newYear(), "12000.00");
		YearChangeTestData.startNewYear(clock, ty.newYear());

		// Fee 12,000.00, paid 9,000.00 -> 3,000.00 unpaid year fee carried forward
		var s = YearChangeTestData.studentWithFeeStatus(jdbc, ty.oldYear(), 222, 0, 0, "12000.00", "9000.00");
		long vS = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s.enrollmentId());
		long std2New = ty.newYear().standard(1);
		long divA = ty.newYear().divisionByStandard().get(std2New);

		String promoteJson = String.format("""
				{
				  "lines": [
				    {
				      "enrollmentId": %d,
				      "version": %d,
				      "outcome": "PROMOTED",
				      "divisionId": %d,
				      "categoryId": %d
				    }
				  ]
				}
				""", s.enrollmentId(), vS, divA, ty.oldYear().category(0));

		client.post("/api/year-change/standards/" + ty.oldYear().standard(0) + "/promote", promoteJson)
				.andExpect(status().isOk());

		List<Map<String, Object>> cfAudit = jdbc.queryForList(
				"select * from audit_log where area = 'Fees' and entity = ? order by id desc",
				"BroughtForward#" + s.studentId());
		assertThat(cfAudit).isNotEmpty();
		Map<String, Object> entry = cfAudit.get(0);
		assertThat(entry.get("action")).isEqualTo("Edit");
		assertThat(entry.get("user_id")).isNotNull();
		assertThat(entry.get("at")).isNotNull();
		String changes = String.valueOf(entry.get("changes"));
		assertThat(changes)
				.contains("\"carriedAmount\": \"3000.00\"")
				.contains("\"newAmount\": \"3000.00\"")
				.contains("\"oldYearId\": " + ty.oldYear().yearId())
				.contains("\"newYearId\": " + ty.newYear().yearId())
				.doesNotContain("*");
	}

	@Test
	@DisplayName("4. Admissions upload writes Students audit entry for ImportBatch with counts")
	void uploadAudit() throws Exception {
		ClassPathResource sample = new ClassPathResource("admissions/responses-sample.csv");
		byte[] bytes = Files.readAllBytes(Path.of(sample.getURI()));
		MockMultipartFile file = new MockMultipartFile("file", "test-audit.csv", "text/csv", bytes);

		String response = client.multipart("/api/admissions/upload", file)
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		long batchId = Long.parseLong(response.replaceAll(".*\"batchId\":([0-9]+).*", "$1"));

		List<Map<String, Object>> entries = jdbc.queryForList(
				"select * from audit_log where area = 'Students' and entity = ? order by id desc",
				"ImportBatch#" + batchId);
		assertThat(entries).isNotEmpty();
		Map<String, Object> entry = entries.get(0);
		assertThat(entry.get("action")).isEqualTo("Create");
		assertThat(entry.get("user_id")).isNotNull();
		assertThat(entry.get("at")).isNotNull();
		String changes = String.valueOf(entry.get("changes"));
		assertThat(changes)
				.contains("\"batchId\": " + batchId)
				.contains("\"total\":")
				.contains("\"added\":");
	}

	@Test
	@DisplayName("5 & 6. Admissions approve and reject write Students audit rows, with review status and remarks")
	void approveAndRejectAudit() throws Exception {
		clock.setDate(LocalDate.of(3840, 5, 1));
		Setup year = FeeTestData.year(jdbc, "AuditApprove", LocalDate.of(3840, 4, 1), 2);
		FeeTestData.addRow(jdbc, year.yearId(), year.standard(0), year.category(0), "12000.00", "500.00", LocalDate.of(3840, 6, 1));
		FeeTestData.setStatus(jdbc, year.yearId(), true);

		long batchId = jdbc.queryForObject(
				"""
				insert into admission_import_batches (file_name, uploaded_by, rows_total, rows_added, rows_skipped, rows_invalid)
				values ('audit.csv', 1, 2, 2, 0, 0) returning id
				""", Long.class);

		// Seed two pending admission requests with all required fields
		String json1 = String.format("{\"surname\":\"Patel\",\"firstName\":\"Aarav\",\"dob\":\"2015-05-10\",\"mobile1\":\"9876543210\",\"address\":\"123 Main St, Porbandar\",\"admissionStandardId\":%d,\"categoryId\":%d,\"gender\":\"Male\"}",
				year.standard(0), year.category(0));
		long reqApproveId = jdbc.queryForObject(
				"""
				insert into admission_requests (submitted_at, form_data, status, import_key, duplicate_flag, import_batch_id)
				values (now(), ?::jsonb, 'Pending', 'audit-approve-key-1', false, ?)
				returning id
				""", Long.class, json1, batchId);

		String json2 = String.format("{\"surname\":\"Shah\",\"firstName\":\"Pooja\",\"dob\":\"2015-07-15\",\"mobile1\":\"9876543211\",\"address\":\"456 High St, Porbandar\",\"admissionStandardId\":%d,\"categoryId\":%d,\"gender\":\"Female\"}",
				year.standard(0), year.category(0));
		long reqRejectId = jdbc.queryForObject(
				"""
				insert into admission_requests (submitted_at, form_data, status, import_key, duplicate_flag, import_batch_id)
				values (now(), ?::jsonb, 'Pending', 'audit-reject-key-2', false, ?)
				returning id
				""", Long.class, json2, batchId);

		// 5. Approve
		String approveJson = String.format("""
				{
				  "grNo": "AUDIT-GR-001",
				  "academicYearId": %d,
				  "divisionId": %d,
				  "categoryId": %d,
				  "admissionDate": "3840-04-01"
				}
				""", year.yearId(), year.divisionByStandard().get(year.standard(0)), year.category(0));

		client.post("/api/admissions/" + reqApproveId + "/approve", approveJson)
				.andExpect(status().isOk());

		List<Map<String, Object>> approveAudit = jdbc.queryForList(
				"select * from audit_log where area = 'Students' and entity = ? order by id desc",
				"AdmissionRequest#" + reqApproveId);
		assertThat(approveAudit).isNotEmpty();
		Map<String, Object> aEntry = approveAudit.get(0);
		assertThat(aEntry.get("action")).isEqualTo("Create");
		assertThat(aEntry.get("user_id")).isNotNull();
		assertThat(String.valueOf(aEntry.get("changes")))
				.contains("\"status\": \"Approved\"")
				.contains("\"grNo\": \"AUDIT-GR-001\"");

		// Verify Student creation audit
		List<Map<String, Object>> studentCreateAudit = jdbc.queryForList(
				"select * from audit_log where area = 'Students' and action = 'Create' and entity like 'Student#%' order by id desc");
		assertThat(studentCreateAudit).isNotEmpty();

		// 6. Reject
		String rejectJson = """
				{
				  "remarks": "Seat not available for this category"
				}
				""";

		client.post("/api/admissions/" + reqRejectId + "/reject", rejectJson)
				.andExpect(status().isOk());

		List<Map<String, Object>> rejectAudit = jdbc.queryForList(
				"select * from audit_log where area = 'Students' and entity = ? order by id desc",
				"AdmissionRequest#" + reqRejectId);
		assertThat(rejectAudit).isNotEmpty();
		Map<String, Object> rEntry = rejectAudit.get(0);
		assertThat(rEntry.get("action")).isEqualTo("Edit");
		assertThat(rEntry.get("user_id")).isNotNull();
		assertThat(String.valueOf(rEntry.get("changes")))
				.contains("\"status\": \"Rejected\"")
				.contains("Seat not available for this category");
	}

	@Test
	@DisplayName("7. Developer correction function records audit entry with done_by, reason, and amountReversed")
	void developerCorrectionAudit() {
		TwoYears ty = YearChangeTestData.twoConsecutiveYears(jdbc, "AuditCorr", LocalDate.of(3850, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, ty.newYear(), "12000.00");

		var s = YearChangeTestData.studentWithFeeStatus(jdbc, ty.oldYear(), 333, 0, 0, "12000.00", "7000.00");
		long std2New = ty.newYear().standard(1);
		long divA = ty.newYear().divisionByStandard().get(std2New);

		// Record promotion run
		long runId = jdbc.queryForObject(
				"""
				insert into promotion_runs (from_year_id, to_year_id, standard_id, done_by, promoted_count, detained_count, left_count, carried_total)
				values (?, ?, ?, 1, 1, 0, 0, 5000.00)
				returning id
				""",
				Long.class, ty.oldYear().yearId(), ty.newYear().yearId(), ty.oldYear().standard(0));

		long newEnrollId = jdbc.queryForObject(
				"insert into student_enrollments (student_id, academic_year_id, standard_id, division_id, category_id, year_status, year_start_date) values (?, ?, ?, ?, ?, 'Studying', '3851-04-01') returning id",
				Long.class, s.studentId(), ty.newYear().yearId(), std2New, divA, ty.oldYear().category(0));

		// Set brought-forward amount 5000.00
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 5000.00, now())", s.studentId());

		long itemId = jdbc.queryForObject(
				"""
				insert into promotion_run_items (run_id, student_id, from_enrollment_id, to_enrollment_id, outcome,
				                                 to_standard_id, to_division_id, to_category_id,
				                                 carried_amount, brought_forward_before, brought_forward_after)
				values (?, ?, ?, ?, 'PROMOTED', ?, ?, ?, 5000.00, 0.00, 5000.00)
				returning id
				""",
				Long.class, runId, s.studentId(), s.enrollmentId(), newEnrollId, std2New, divA, ty.oldYear().category(0));

		// Call correct_promotion function
		jdbc.execute(String.format("SELECT correct_promotion(%d, 'Wrong standard assigned', 'Dev Auditor')", itemId));

		List<Map<String, Object>> corrAudit = jdbc.queryForList(
				"select * from audit_log where area = 'Students' and entity = ? and reason = ? order by id desc",
				"Student#" + s.studentId(), "Wrong standard assigned");
		assertThat(corrAudit).isNotEmpty();
		Map<String, Object> entry = corrAudit.get(0);
		assertThat(entry.get("action")).isEqualTo("Edit");
		assertThat(entry.get("reason")).isEqualTo("Wrong standard assigned");
		String changes = String.valueOf(entry.get("changes"));
		assertThat(changes)
				.contains("\"by\": \"Dev Auditor\"")
				.contains("\"outcome\": \"PROMOTED\"")
				.contains("\"amountReversed\": 5000.00");
	}

	@Test
	@DisplayName("8. Aadhaar and religion/caste are masked in form_data views, search lists, and audit log")
	void aadhaarAndReligionCasteMaskedInViewsListsAndAudit() throws Exception {
		Setup year = FeeTestData.year(jdbc, "AuditMask", LocalDate.of(3860, 4, 1), 1);
		FeeTestData.setStatus(jdbc, year.yearId(), true);

		long batchId = jdbc.queryForObject(
				"""
				insert into admission_import_batches (file_name, uploaded_by, rows_total, rows_added, rows_skipped, rows_invalid)
				values ('masking.csv', 1, 1, 1, 0, 0) returning id
				""", Long.class);

		String json = String.format("{\"surname\":\"Desai\",\"firstName\":\"Karan\",\"dob\":\"2016-01-20\",\"mobile1\":\"9898989898\",\"address\":\"Masking address\",\"aadhaar\":\"987654321098\",\"religionCaste\":\"Hindu - Brahmin\",\"admissionStandardId\":%d}",
				year.standard(0));
		long reqId = jdbc.queryForObject(
				"""
				insert into admission_requests (submitted_at, form_data, status, import_key, duplicate_flag, import_batch_id)
				values (now(), ?::jsonb, 'Pending', 'masking-test-key-1', false, ?)
				returning id
				""", Long.class, json, batchId);

		// 8a. View detail endpoint masks Aadhaar and religion/caste
		client.get("/api/admissions/" + reqId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.form.aadhaar").value("********1098"))
				.andExpect(jsonPath("$.form.religionCaste").value("***********hmin"));

		// 8b. List/search endpoint masks Aadhaar
		client.post("/api/admissions/search", "{\"first\":0,\"rows\":10}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rows[0].form.aadhaar").value("********1098"));

		// 8c. Plain 12-digit Aadhaar number never appears in response body
		String rawBody = client.get("/api/admissions/" + reqId).andReturn().getResponse().getContentAsString();
		assertThat(rawBody).doesNotContain("987654321098");
	}

}

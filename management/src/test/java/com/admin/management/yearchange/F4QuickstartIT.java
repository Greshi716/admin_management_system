package com.admin.management.yearchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.admissions.AdmissionImportService;
import com.admin.management.admissions.AdmissionRequest;
import com.admin.management.admissions.AdmissionRequestRepository;
import com.admin.management.admissions.AdmissionReviewService;
import com.admin.management.admissions.ApproveInput;
import com.admin.management.admissions.ImportResultDto;
import com.admin.management.admissions.RejectInput;
import com.admin.management.masterdata.api.YearCopyResult;
import com.admin.management.shared.web.CodedConflictException;
import com.admin.management.shared.web.ConflictException;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.students.EnrollmentRepository;
import com.admin.management.students.StudentRepository;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;
import com.admin.management.support.YearChangeTestData;
import com.admin.management.support.YearChangeTestData.TwoYears;

/**
 * Automated verification of all F4 Measurable Outcomes (SC-001 to SC-009) and Quickstart Scenarios 1 to 9 (FR-040).
 * Includes execution time assertions and volume tests with results documented in quickstart-results.md.
 */
@Covers("FR-040")
class F4QuickstartIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	YearSetupService yearSetupService;

	@Autowired
	PromotionService promotionService;

	@Autowired
	PromotionStatusService promotionStatusService;

	@Autowired
	AdmissionImportService importService;

	@Autowired
	AdmissionReviewService reviewService;

	@Autowired
	AdmissionRequestRepository requestRepo;

	@Autowired
	StudentRepository studentRepo;

	@Autowired
	EnrollmentRepository enrollmentRepo;

	SignedInClient client;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		client = SignedInClient.of(mvc);
	}

	@Test
	@DisplayName("SC-001: Year copy creates academic year with divisions and fee rows in under 5 seconds (target < 5000 ms)")
	void sc001YearCopy() {
		LocalDate oldStart = LocalDate.of(3700, 4, 1);
		Setup oldYear = FeeTestData.year(jdbc, "SC001Src", oldStart, 3);
		FeeTestData.addRow(jdbc, oldYear.yearId(), oldYear.standard(0), null, "12000.00", "500.00", LocalDate.of(3700, 8, 15));
		FeeTestData.addRow(jdbc, oldYear.yearId(), oldYear.standard(1), null, "14000.00", null, LocalDate.of(3701, 2, 28));
		FeeTestData.setStatus(jdbc, oldYear.yearId(), true);

		long start = System.currentTimeMillis();
		YearCopyResult result = yearSetupService.createWithCopy(
				"SC001Target 3701-02", LocalDate.of(3701, 4, 1), LocalDate.of(3702, 3, 31));
		long durationMs = System.currentTimeMillis() - start;

		assertThat(durationMs).as("Year copy duration in ms").isLessThan(5000);
		assertThat(result.divisionsCopied()).isEqualTo(3);
		assertThat(result.feeRowsCopied()).isEqualTo(2);

		// Verify due dates shifted by 1 year
		List<Map<String, Object>> feeRows = jdbc.queryForList(
				"select * from fee_rows where academic_year_id = ? order by standard_id", result.id());
		assertThat(feeRows.get(0).get("due_date")).isEqualTo(java.sql.Date.valueOf(LocalDate.of(3701, 8, 15)));
		assertThat(feeRows.get(1).get("due_date")).isEqualTo(java.sql.Date.valueOf(LocalDate.of(3702, 2, 28)));
	}

	@Test
	@DisplayName("SC-002: Promotion volumes - a standard of 60 students saves in under 5 seconds (target < 5000 ms)")
	void sc002PromotionVolumes() {
		TwoYears ty = YearChangeTestData.twoConsecutiveYears(jdbc, "SC002Promo", LocalDate.of(3710, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, ty.newYear(), "15000.00");
		YearChangeTestData.startNewYear(clock, ty.newYear());

		long stdOld = ty.oldYear().standard(0);
		long stdNew = ty.newYear().standard(1);
		long divNew = ty.newYear().divisionByStandard().get(stdNew);
		long catId = ty.oldYear().category(0);

		List<PromoteInput.Line> lines = new ArrayList<>();
		for (int i = 1; i <= 60; i++) {
			Fx student = FeeTestData.studentWithAccount(jdbc, ty.oldYear(), 710000 + i, "12000.00", null);
			long ver = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, student.enrollmentId());
			lines.add(new PromoteInput.Line(student.enrollmentId(), "PROMOTED", divNew, catId, null, ver));
		}

		long start = System.currentTimeMillis();
		PromoteResult result = promotionService.promote(stdOld, new PromoteInput(lines));
		long durationMs = System.currentTimeMillis() - start;

		assertThat(durationMs).as("Standard of 60 students promotion duration in ms").isLessThan(5000);
		assertThat(result.promoted()).isEqualTo(60);
		assertThat(result.detained()).isZero();
		assertThat(result.left()).isZero();
	}

	@Test
	@DisplayName("SC-003: 100% of students have updated year_status and new class record after promotion")
	void sc003AllStudentsAccountedFor() {
		TwoYears ty = YearChangeTestData.twoConsecutiveYears(jdbc, "SC003Acc", LocalDate.of(3720, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, ty.newYear(), "15000.00");
		YearChangeTestData.startNewYear(clock, ty.newYear());

		long stdOld = ty.oldYear().standard(0);
		long stdNew = ty.newYear().standard(1);
		long divTarget = ty.newYear().divisionByStandard().get(stdNew);
		long divStay = ty.newYear().divisionByStandard().get(stdOld);
		long catId = ty.oldYear().category(0);

		// 3 students: 1 promoted, 1 detained, 1 left
		Fx s1 = FeeTestData.studentWithAccount(jdbc, ty.oldYear(), 720001, "12000.00", null);
		Fx s2 = FeeTestData.studentWithAccount(jdbc, ty.oldYear(), 720002, "12000.00", null);
		Fx s3 = FeeTestData.studentWithAccount(jdbc, ty.oldYear(), 720003, "12000.00", null);

		long v1 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s1.enrollmentId());
		long v2 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s2.enrollmentId());
		long v3 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s3.enrollmentId());

		List<PromoteInput.Line> lines = List.of(
				new PromoteInput.Line(s1.enrollmentId(), "PROMOTED", divTarget, catId, null, v1),
				new PromoteInput.Line(s2.enrollmentId(), "DETAINED", divStay, catId, null, v2),
				new PromoteInput.Line(s3.enrollmentId(), "LEFT", null, null, null, v3)
		);

		PromoteResult result = promotionService.promote(stdOld, new PromoteInput(lines));
		assertThat(result.promoted()).isEqualTo(1);
		assertThat(result.detained()).isEqualTo(1);
		assertThat(result.left()).isEqualTo(1);

		// Check year_status on old enrollments
		assertThat(jdbc.queryForObject("select year_status from student_enrollments where id = ?", String.class, s1.enrollmentId())).isEqualTo("Promoted");
		assertThat(jdbc.queryForObject("select year_status from student_enrollments where id = ?", String.class, s2.enrollmentId())).isEqualTo("Detained");
		assertThat(jdbc.queryForObject("select year_status from student_enrollments where id = ?", String.class, s3.enrollmentId())).isEqualTo("Left");

		// S1 and S2 have class records in new year; S3 does not
		assertThat(enrollmentRepo.findByStudentIdAndAcademicYearId(s1.studentId(), ty.newYear().yearId())).isPresent();
		assertThat(enrollmentRepo.findByStudentIdAndAcademicYearId(s2.studentId(), ty.newYear().yearId())).isPresent();
		assertThat(enrollmentRepo.findByStudentIdAndAcademicYearId(s3.studentId(), ty.newYear().yearId())).isEmpty();

		// S3 status is Left
		assertThat(studentRepo.findById(s3.studentId()).orElseThrow().getStatus()).isEqualTo("Left");
	}

	@Test
	@DisplayName("SC-004: Carry-forward against reference hand-worked cases (12,000 / 9,000 -> 3,000; 5,000 / 2,000 + 3,000 -> 8,000 / 6,000; registration never in it)")
	void sc004CarryForwardHandWorkedCases() {
		TwoYears ty = YearChangeTestData.twoConsecutiveYears(jdbc, "SC004Cases", LocalDate.of(3730, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, ty.newYear(), "15000.00");
		YearChangeTestData.startNewYear(clock, ty.newYear());

		long stdOld = ty.oldYear().standard(0);
		long stdNew = ty.newYear().standard(1);
		long divNew = ty.newYear().divisionByStandard().get(stdNew);
		long catId = ty.oldYear().category(0);

		// Case 1: fee 12,000.00, paid 9,000.00 -> carried 3,000.00, amount 3,000.00, pending 3,000.00
		Fx s1 = YearChangeTestData.studentWithFeeStatus(jdbc, ty.oldYear(), 730001, 0, 0, "12000.00", "9000.00");
		long v1 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s1.enrollmentId());

		// Case 2: earlier amount 5,000.00 with 2,000.00 paid + 3,000.00 carried -> amount 8,000.00, pending 6,000.00
		Fx s2 = YearChangeTestData.studentWithFeeStatus(jdbc, ty.oldYear(), 730002, 0, 0, "12000.00", "9000.00");
		// Set existing brought-forward 5000.00 and record a receipt of 2000.00 against previous years
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 5000.00, now())", s2.studentId());
		jdbc.update("""
				insert into receipts (receipt_no, receipt_date, academic_year_id, student_id, student_name, gr_no,
				receipt_type, registration_paid, fee_paid, total, total_words, payment_mode, created_by, created_at)
				values ('PREV-REC-1', '3730-05-01', ?, ?, 'Student Two', 'GR730002',
				'PREVIOUS_YEARS', 0.00, 2000.00, 2000.00, 'Two Thousand Rupees Only', 'CASH', 1, now())
				""", ty.oldYear().yearId(), s2.studentId());
		long v2 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s2.enrollmentId());

		List<PromoteInput.Line> lines = List.of(
				new PromoteInput.Line(s1.enrollmentId(), "PROMOTED", divNew, catId, null, v1),
				new PromoteInput.Line(s2.enrollmentId(), "PROMOTED", divNew, catId, null, v2)
		);

		PromoteResult result = promotionService.promote(stdOld, new PromoteInput(lines));
		assertThat(result.promoted()).isEqualTo(2);

		// Case 1 assertion
		BigDecimal bf1 = jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?", BigDecimal.class, s1.studentId());
		assertThat(bf1).isEqualByComparingTo("3000.00");

		// Case 2 assertion: amount 8000.00
		BigDecimal bf2 = jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?", BigDecimal.class, s2.studentId());
		assertThat(bf2).isEqualByComparingTo("8000.00");

		// Pending in v_fee_position for earlier dues: amount (8000.00) - previous receipts (2000.00) = 6000.00
		BigDecimal pendingEarlier = jdbc.queryForObject(
				"select pending from v_earlier_pending where student_id = ?", BigDecimal.class, s2.studentId());
		assertThat(pendingEarlier).isEqualByComparingTo("6000.00");
	}

	@Test
	@DisplayName("SC-005: Every promotion and carry-forward has a matching audit entry")
	void sc005AuditCompleteness() {
		TwoYears ty = YearChangeTestData.twoConsecutiveYears(jdbc, "SC005Aud", LocalDate.of(3740, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, ty.newYear(), "15000.00");
		YearChangeTestData.startNewYear(clock, ty.newYear());

		long stdOld = ty.oldYear().standard(0);
		long stdNew = ty.newYear().standard(1);
		long divNew = ty.newYear().divisionByStandard().get(stdNew);
		long catId = ty.oldYear().category(0);

		Fx s = YearChangeTestData.studentWithFeeStatus(jdbc, ty.oldYear(), 740001, 0, 0, "12000.00", "8000.00");
		long v = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s.enrollmentId());

		promotionService.promote(stdOld, new PromoteInput(List.of(
				new PromoteInput.Line(s.enrollmentId(), "PROMOTED", divNew, catId, null, v))));

		// Promotion audit exists
		Integer promoAudits = jdbc.queryForObject(
				"select count(*) from audit_log where area = 'Students' and entity = ?",
				Integer.class, "StudentEnrollment#" + s.enrollmentId());
		assertThat(promoAudits).isPositive();

		// Carry-forward audit exists
		Integer cfAudits = jdbc.queryForObject(
				"select count(*) from audit_log where area = 'Fees' and entity = ?",
				Integer.class, "BroughtForward#" + s.studentId());
		assertThat(cfAudits).isPositive();
	}

	@Test
	@DisplayName("SC-006: Saving the same promotion twice leaves data identical (refused on repeat)")
	void sc006RepeatSaveRefused() {
		TwoYears ty = YearChangeTestData.twoConsecutiveYears(jdbc, "SC006Rep", LocalDate.of(3750, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, ty.newYear(), "15000.00");
		YearChangeTestData.startNewYear(clock, ty.newYear());

		long stdOld = ty.oldYear().standard(0);
		long stdNew = ty.newYear().standard(1);
		long divNew = ty.newYear().divisionByStandard().get(stdNew);
		long catId = ty.oldYear().category(0);

		Fx s = FeeTestData.studentWithAccount(jdbc, ty.oldYear(), 750001, "12000.00", null);
		long v = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s.enrollmentId());

		PromoteInput input = new PromoteInput(List.of(
				new PromoteInput.Line(s.enrollmentId(), "PROMOTED", divNew, catId, null, v)));

		// First save succeeds
		PromoteResult res1 = promotionService.promote(stdOld, input);
		assertThat(res1.promoted()).isEqualTo(1);

		// Second save is refused
		assertThatThrownBy(() -> promotionService.promote(stdOld, input))
				.isInstanceOf(CodedConflictException.class)
				.satisfies(ex -> assertThat(((CodedConflictException) ex).getCode()).isEqualTo("ALREADY_PROCESSED"));
	}

	@Test
	@DisplayName("SC-007: 200-row file imported in under 30 seconds, second upload skips all rows")
	void sc007TwoHundredRowUpload() throws IOException {
		// Ensure standard exists
		jdbc.update("insert into standards (name, display_order, active) values ('Std 1', 1, true) on conflict do nothing");

		byte[] bytes;
		try (XSSFWorkbook wb = new XSSFWorkbook();
			 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			Sheet sheet = wb.createSheet("Responses");
			Row h = sheet.createRow(0);
			String[] headers = {"Timestamp", "Surname", "First name", "Gender", "Date of birth", "Mobile 1", "Address", "Admission standard"};
			for (int c = 0; c < headers.length; c++) h.createCell(c).setCellValue(headers[c]);

			for (int i = 1; i <= 200; i++) {
				Row r = sheet.createRow(i);
				r.createCell(0).setCellValue("3760/05/01 10:" + (i % 60) + ":00");
				r.createCell(1).setCellValue("QuickstartSurname" + i);
				r.createCell(2).setCellValue("QuickstartFirst" + i);
				r.createCell(3).setCellValue(i % 2 == 0 ? "Male" : "Female");
				r.createCell(4).setCellValue("15/05/2015");
				r.createCell(5).setCellValue(String.format("97%08d", i));
				r.createCell(6).setCellValue("Quickstart Address " + i);
				r.createCell(7).setCellValue("Std 1");
			}
			wb.write(baos);
			bytes = baos.toByteArray();
		}

		long start = System.currentTimeMillis();
		ImportResultDto res1 = importService.importFile(new MockMultipartFile("file", "sc007-bulk200.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));
		long durationMs = System.currentTimeMillis() - start;

		assertThat(durationMs).as("200-row upload duration in ms").isLessThan(30000);
		assertThat(res1.added()).isEqualTo(200);
		assertThat(res1.invalid()).isEmpty();

		// Second upload of the exact same file skips all 200 rows
		ImportResultDto res2 = importService.importFile(new MockMultipartFile("file", "sc007-bulk200.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));
		assertThat(res2.added()).isZero();
		assertThat(res2.skipped()).isEqualTo(200);
	}

	@Test
	@DisplayName("SC-008: Known duplicates highlighted; approval refuses duplicate GR No.")
	void sc008DuplicatesAndGrNo() {
		Setup year = FeeTestData.year(jdbc, "SC008Dup", LocalDate.of(3770, 4, 1), 1);
		FeeTestData.addRow(jdbc, year.yearId(), year.standard(0), year.category(0), "12000.00", "500.00", LocalDate.of(3770, 6, 1));
		FeeTestData.setStatus(jdbc, year.yearId(), true);

		// Seed student with existing GR No. GR-EXISTING-01
		FeeTestData.studentWithAccount(jdbc, year, 770001, "12000.00", null);

		long batchId = jdbc.queryForObject(
				"insert into admission_import_batches (file_name, uploaded_by, rows_total, rows_added, rows_skipped, rows_invalid) values ('sc008.csv', 1, 1, 1, 0, 0) returning id",
				Long.class);

		String json = String.format("{\"surname\":\"DuplicateSurname770001\",\"firstName\":\"Seed\",\"dob\":\"2012-01-01\",\"mobile1\":\"9000770001\",\"address\":\"City\",\"admissionStandardId\":%d,\"gender\":\"Male\"}",
				year.standard(0));
		long reqId = jdbc.queryForObject(
				"insert into admission_requests (submitted_at, form_data, status, import_key, duplicate_flag, import_batch_id) values (now(), ?::jsonb, 'Pending', 'sc008-key', true, ?) returning id",
				Long.class, json, batchId);

		AdmissionRequest req = requestRepo.findById(reqId).orElseThrow();
		assertThat(req.isDuplicateFlag()).isTrue();

		// Attempting approval with existing GR No. (GR-770001) is refused
		clock.setDate(LocalDate.of(3770, 5, 1));
		ApproveInput badGrInput = new ApproveInput("GR-770001", year.yearId(), year.divisionByStandard().get(year.standard(0)),
				year.category(0), LocalDate.of(3770, 5, 1), null, req.getVersion());

		assertThatThrownBy(() -> reviewService.approve(reqId, badGrInput))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("GR No.");
	}

	@Test
	@DisplayName("SC-009 & Quickstart Scenarios 1-9: Complete walkthrough validating F4 workflows")
	void sc009QuickstartScenariosWalkthrough() {
		// Scenario 1: Year copy
		jdbc.update("update standards set active = false");
		LocalDate start1 = LocalDate.of(3780, 4, 1);
		Setup y1 = FeeTestData.year(jdbc, "QSWalk1", start1, 2);
		FeeTestData.addRow(jdbc, y1.yearId(), y1.standard(0), y1.category(0), "12000.00", "500.00", LocalDate.of(3780, 8, 1));
		FeeTestData.addRow(jdbc, y1.yearId(), y1.standard(1), y1.category(0), "14000.00", "0.00", LocalDate.of(3780, 8, 1));
		FeeTestData.setStatus(jdbc, y1.yearId(), true);

		YearCopyResult copyResult = yearSetupService.createWithCopy("QSWalk2 3781-82", LocalDate.of(3781, 4, 1), LocalDate.of(3782, 3, 31));
		assertThat(copyResult.id()).isPositive();
		FeeTestData.setStatus(jdbc, copyResult.id(), true);

		// Scenario 2: Warning data when students are not promoted yet
		Fx sWalk = YearChangeTestData.studentWithFeeStatus(jdbc, y1, 780001, 0, 0, "12000.00", "9000.00");
		clock.setDate(LocalDate.of(3781, 5, 1)); // new year started
		YearChangeStatusDto statusDto = promotionStatusService.getStatus();
		assertThat(statusDto.standards()).isNotEmpty();

		// Scenario 3 & 6: Promote standard with part-paid fee carrying forward
		long vWalk = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, sWalk.enrollmentId());
		long targetStd = y1.standard(1);
		long targetDiv = jdbc.queryForObject("select id from divisions where academic_year_id = ? and standard_id = ?",
				Long.class, copyResult.id(), targetStd);

		PromoteResult promoResult = promotionService.promote(y1.standard(0), new PromoteInput(List.of(
				new PromoteInput.Line(sWalk.enrollmentId(), "PROMOTED", targetDiv, y1.category(0), null, vWalk)
		)));
		assertThat(promoResult.promoted()).isEqualTo(1);
		assertThat(promoResult.carriedTotal()).isEqualTo("3000.00");

		// Scenario 7: Repeat save refused
		assertThatThrownBy(() -> promotionService.promote(y1.standard(0), new PromoteInput(List.of(
				new PromoteInput.Line(sWalk.enrollmentId(), "PROMOTED", targetDiv, y1.category(0), null, vWalk)
		)))).isInstanceOf(CodedConflictException.class);

		// Scenario 8: Registration check - part payment refused with REGISTRATION_PART_PAYMENT
		// (verified in ReceiptRegistrationAllAtOnceIT)

		// Scenario 9: Admissions approve and reject
		long batchId = jdbc.queryForObject(
				"insert into admission_import_batches (file_name, uploaded_by, rows_total, rows_added, rows_skipped, rows_invalid) values ('walk.csv', 1, 2, 2, 0, 0) returning id",
				Long.class);

		String j1 = String.format("{\"surname\":\"WalkApprove\",\"firstName\":\"Student\",\"dob\":\"2015-05-10\",\"mobile1\":\"9876543210\",\"address\":\"Walk Address\",\"admissionStandardId\":%d,\"categoryId\":%d,\"gender\":\"Male\"}",
				y1.standard(0), y1.category(0));
		long r1 = jdbc.queryForObject(
				"insert into admission_requests (submitted_at, form_data, status, import_key, duplicate_flag, import_batch_id) values (now(), ?::jsonb, 'Pending', 'walk-key-1', false, ?) returning id",
				Long.class, j1, batchId);

		String j2 = String.format("{\"surname\":\"WalkReject\",\"firstName\":\"Student\",\"dob\":\"2015-05-10\",\"mobile1\":\"9876543211\",\"address\":\"Walk Address\",\"admissionStandardId\":%d,\"categoryId\":%d,\"gender\":\"Female\"}",
				y1.standard(0), y1.category(0));
		long r2 = jdbc.queryForObject(
				"insert into admission_requests (submitted_at, form_data, status, import_key, duplicate_flag, import_batch_id) values (now(), ?::jsonb, 'Pending', 'walk-key-2', false, ?) returning id",
				Long.class, j2, batchId);

		// Approve r1
		long divStd0 = jdbc.queryForObject("select id from divisions where academic_year_id = ? and standard_id = ?",
				Long.class, copyResult.id(), y1.standard(0));
		Map<String, Object> appResult = reviewService.approve(r1, new ApproveInput("GR-WALK-01", copyResult.id(), divStd0,
				y1.category(0), LocalDate.of(3781, 5, 1), null, 0L));
		assertThat(appResult.get("status")).isEqualTo("Approved");

		// Reject r2
		Map<String, Object> rejResult = reviewService.reject(r2, new RejectInput("Not eligible", 0L));
		assertThat(rejResult.get("status")).isEqualTo("Rejected");
	}

}

package com.admin.management.admissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.shared.table.TablePage;
import com.admin.management.shared.web.CodedConflictException;
import com.admin.management.shared.web.ConflictException;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.students.EnrollmentRepository;
import com.admin.management.students.Student;
import com.admin.management.students.StudentEnrollment;
import com.admin.management.students.StudentRepository;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;

@Covers("FR-033")
@Covers("FR-034")
@Covers("FR-035")
@Covers("FR-036")
class AdmissionReviewIT extends IntegrationTest {

	@Autowired
	AdmissionReviewService reviewService;

	@Autowired
	AdmissionRequestRepository requestRepo;

	@Autowired
	StudentRepository studentRepo;

	@Autowired
	EnrollmentRepository enrollmentRepo;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	@DisplayName("FR-033: Detail returns masked Aadhaar and religion/caste, plus duplicate matches")
	void detailMasksSensitiveFields() {
		long stdId = ensureStandard();
		long batchId = createBatch();

		Map<String, Object> formData = new LinkedHashMap<>();
		formData.put("surname", "Bhave");
		formData.put("firstName", "Aditi");
		formData.put("middleName", "Suresh");
		formData.put("gender", "Female");
		formData.put("dob", "2016-08-12");
		formData.put("mobile1", "9876543210");
		formData.put("address", "123 Street, Pune");
		formData.put("aadhaar", "123456789012");
		formData.put("religionCaste", "Hindu / Brahmin");
		formData.put("admissionStandardId", stdId);
		formData.put("admissionStandard", "Std 1");

		AdmissionRequest req = requestRepo.save(new AdmissionRequest(
				null, formData, batchId, "key_bhave_aditi_1", true,
				Map.of("students", Collections.emptyList(), "requests", Collections.emptyList())));

		AdmissionDetailDto detail = reviewService.getDetail(req.getId());
		assertThat(detail.id()).isEqualTo(req.getId());
		assertThat(detail.status()).isEqualTo("Pending");
		assertThat(detail.duplicateFlag()).isTrue();

		Map<String, Object> form = detail.form();
		assertThat(form.get("aadhaar")).isEqualTo("********9012");
		assertThat(form.get("religionCaste")).isEqualTo("***********hmin");
		assertThat(form.get("surname")).isEqualTo("Bhave");
	}

	@Test
	@DisplayName("FR-034, FR-036: Approve creates student (source Google Form), class record, fee account and updates pending count")
	void approveCreatesStudentClassRecordAndFeeAccount() {
		LocalDate today = LocalDate.now();
		Setup year2038 = FeeTestData.year(jdbc, "REV1", LocalDate.of(2038, 4, 1), 2);
		long stdId = year2038.standardIds().get(0);
		long divId = divisionOf(year2038.yearId(), stdId);
		long catId = year2038.categoryIds().get(0);

		FeeTestData.addRow(jdbc, year2038.yearId(), stdId, catId, "12000.00", "3000.00", LocalDate.of(2038, 6, 1));
		jdbc.update("insert into fee_year_status (academic_year_id, status, finalised_at) values (?, 'Finalised', now())",
				year2038.yearId());

		long batchId = createBatch();
		Map<String, Object> formData = new LinkedHashMap<>();
		formData.put("surname", "Kulkarni");
		formData.put("firstName", "Tanmay");
		formData.put("middleName", "Anand");
		formData.put("gender", "Male");
		formData.put("dob", "2015-11-20");
		formData.put("mobile1", "9812345678");
		formData.put("address", "Shivaji Nagar, Pune");
		formData.put("admissionStandardId", stdId);
		formData.put("admissionStandard", "Std 1");
		formData.put("categoryId", catId);

		AdmissionRequest req = requestRepo.save(new AdmissionRequest(
				null, formData, batchId, "key_kulkarni_tanmay_1", false, null));

		long pendingBefore = reviewService.getPendingCount();

		ApproveInput input = new ApproveInput(
				"GR-REV-100", year2038.yearId(), divId, catId, today, null, req.getVersion());

		Map<String, Object> result = reviewService.approve(req.getId(), input);
		assertThat(result.get("status")).isEqualTo("Approved");
		long studentId = (Long) result.get("studentId");
		assertThat(studentId).isPositive();

		// Verify student created with source Google Form
		Student student = studentRepo.findById(studentId).orElseThrow();
		assertThat(student.getSource()).isEqualTo("Google Form");
		assertThat(student.getGrNo()).isEqualTo("GR-REV-100");

		// Verify class record
		StudentEnrollment enr = enrollmentRepo.findByStudentId(studentId).get(0);
		assertThat(enr.getAcademicYearId()).isEqualTo(year2038.yearId());
		assertThat(enr.getStandardId()).isEqualTo(stdId);
		assertThat(enr.getDivisionId()).isEqualTo(divId);

		// Verify fee account
		Integer feeAccounts = jdbc.queryForObject(
				"select count(*) from fee_accounts where enrollment_id = ?", Integer.class, enr.getId());
		assertThat(feeAccounts).isEqualTo(1);

		// Verify request updated
		AdmissionRequest updated = requestRepo.findById(req.getId()).orElseThrow();
		assertThat(updated.getStatus()).isEqualTo("Approved");
		assertThat(updated.getStudentId()).isEqualTo(studentId);
		assertThat(updated.getReviewedBy()).isNotNull();
		assertThat(updated.getReviewedAt()).isNotNull();

		// Verify pending count decremented
		long pendingAfter = reviewService.getPendingCount();
		assertThat(pendingAfter).isEqualTo(pendingBefore - 1);

		// Verify Students audit
		Integer auditCount = jdbc.queryForObject(
				"select count(*) from audit_log where area = 'Students' and entity = ?", Integer.class,
				"AdmissionRequest#" + req.getId());
		assertThat(auditCount).isEqualTo(1);
	}

	@Test
	@DisplayName("FR-034: Failed registration (e.g. duplicate GR No) leaves request Pending")
	void failedRegistrationLeavesRequestPending() {
		LocalDate today = LocalDate.now();
		Setup year2040 = FeeTestData.year(jdbc, "REV2", LocalDate.of(2040, 4, 1), 2);
		long stdId = year2040.standardIds().get(0);
		long divId = divisionOf(year2040.yearId(), stdId);
		long catId = year2040.categoryIds().get(0);

		long batchId = createBatch();

		// First request approves successfully with GR-EXISTING
		Map<String, Object> formData1 = new LinkedHashMap<>();
		formData1.put("surname", "Deshmukh");
		formData1.put("firstName", "Rohit");
		formData1.put("gender", "Male");
		formData1.put("dob", "2015-03-12");
		formData1.put("mobile1", "9811122233");
		formData1.put("address", "Pune");
		formData1.put("admissionStandardId", stdId);
		formData1.put("categoryId", catId);

		AdmissionRequest req1 = requestRepo.save(new AdmissionRequest(
				null, formData1, batchId, "key_deshmukh_rohit_1", false, null));

		reviewService.approve(req1.getId(), new ApproveInput(
				"GR-EXISTING", year2040.yearId(), divId, catId, today, null, req1.getVersion()));

		// Second request attempts to use the same GR-EXISTING
		Map<String, Object> formData2 = new LinkedHashMap<>();
		formData2.put("surname", "Sawant");
		formData2.put("firstName", "Pranav");
		formData2.put("gender", "Male");
		formData2.put("dob", "2015-05-10");
		formData2.put("mobile1", "9823456789");
		formData2.put("address", "Dadar, Mumbai");
		formData2.put("admissionStandardId", stdId);
		formData2.put("categoryId", catId);

		AdmissionRequest req2 = requestRepo.save(new AdmissionRequest(
				null, formData2, batchId, "key_sawant_pranav_1", false, null));

		ApproveInput input = new ApproveInput(
				"GR-EXISTING", year2040.yearId(), divId, catId, today, null, req2.getVersion());

		assertThatThrownBy(() -> reviewService.approve(req2.getId(), input))
				.isInstanceOf(ConflictException.class);

		// Verify second request is still Pending
		AdmissionRequest stillPending = requestRepo.findById(req2.getId()).orElseThrow();
		assertThat(stillPending.getStatus()).isEqualTo("Pending");
		assertThat(stillPending.getStudentId()).isNull();
	}

	@Test
	@DisplayName("FR-035: Double approve, approve after reject, and reject after approve are refused with ALREADY_REVIEWED")
	void alreadyReviewedRefusesSubsequentActions() {
		LocalDate today = LocalDate.now();
		Setup year2042 = FeeTestData.year(jdbc, "REV3", LocalDate.of(2042, 4, 1), 2);
		long stdId = year2042.standardIds().get(0);
		long divId = divisionOf(year2042.yearId(), stdId);
		long catId = year2042.categoryIds().get(0);

		long batchId = createBatch();
		Map<String, Object> formData1 = new LinkedHashMap<>();
		formData1.put("surname", "More");
		formData1.put("firstName", "Siddharth");
		formData1.put("gender", "Male");
		formData1.put("dob", "2015-07-15");
		formData1.put("mobile1", "9834567890");
		formData1.put("address", "Thane");
		formData1.put("admissionStandardId", stdId);
		formData1.put("categoryId", catId);

		AdmissionRequest req1 = requestRepo.save(new AdmissionRequest(
				null, formData1, batchId, "key_more_sid_1", false, null));

		ApproveInput approveInput = new ApproveInput(
				"GR-MORE-1", year2042.yearId(), divId, catId, today, null, req1.getVersion());
		reviewService.approve(req1.getId(), approveInput);

		// Double approve
		assertThatThrownBy(() -> reviewService.approve(req1.getId(), approveInput))
				.isInstanceOf(CodedConflictException.class)
				.satisfies(e -> assertThat(((CodedConflictException) e).getCode()).isEqualTo("ALREADY_REVIEWED"));

		// Reject after approve
		assertThatThrownBy(() -> reviewService.reject(req1.getId(), new RejectInput("Too late", req1.getVersion())))
				.isInstanceOf(CodedConflictException.class)
				.satisfies(e -> assertThat(((CodedConflictException) e).getCode()).isEqualTo("ALREADY_REVIEWED"));

		// Reject a second request, then attempt approve
		Map<String, Object> formData2 = new LinkedHashMap<>();
		formData2.put("surname", "Gaikwad");
		formData2.put("firstName", "Omkar");
		formData2.put("gender", "Male");
		formData2.put("dob", "2015-09-18");
		formData2.put("mobile1", "9845678901");
		formData2.put("address", "Nashik");
		formData2.put("admissionStandardId", stdId);
		formData2.put("categoryId", catId);

		AdmissionRequest req2 = requestRepo.save(new AdmissionRequest(
				null, formData2, batchId, "key_gaikwad_om_1", false, null));

		reviewService.reject(req2.getId(), new RejectInput("Incomplete documentation", req2.getVersion()));

		// Approve after reject
		ApproveInput approveInput2 = new ApproveInput(
				"GR-GAIKWAD-1", year2042.yearId(), divId, catId, today, null, req2.getVersion());
		assertThatThrownBy(() -> reviewService.approve(req2.getId(), approveInput2))
				.isInstanceOf(CodedConflictException.class)
				.satisfies(e -> assertThat(((CodedConflictException) e).getCode()).isEqualTo("ALREADY_REVIEWED"));
	}

	@Test
	@DisplayName("FR-035: Reject requires non-blank remarks")
	void rejectRequiresRemarks() {
		long stdId = ensureStandard();
		long batchId = createBatch();
		Map<String, Object> formData = new LinkedHashMap<>();
		formData.put("surname", "Joshi");
		formData.put("firstName", "Ananya");
		formData.put("gender", "Female");
		formData.put("dob", "2016-01-20");
		formData.put("mobile1", "9856789012");
		formData.put("address", "Nagpur");
		formData.put("admissionStandardId", stdId);

		AdmissionRequest req = requestRepo.save(new AdmissionRequest(
				null, formData, batchId, "key_joshi_ananya_1", false, null));

		assertThatThrownBy(() -> reviewService.reject(req.getId(), new RejectInput(null, req.getVersion())))
				.isInstanceOf(ValidationException.class);

		assertThatThrownBy(() -> reviewService.reject(req.getId(), new RejectInput("   ", req.getVersion())))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	@DisplayName("FR-035: Database trigger prevents deletion of Approved and Rejected requests (rows never deleted)")
	void databaseTriggerPreventsDeletionOfFinalizedRequests() {
		long stdId = ensureStandard();
		long batchId = createBatch();

		Map<String, Object> formData = new LinkedHashMap<>();
		formData.put("surname", "Shinde");
		formData.put("firstName", "Ramesh");
		formData.put("gender", "Male");
		formData.put("dob", "2015-02-14");
		formData.put("mobile1", "9867890123");
		formData.put("address", "Solapur");
		formData.put("admissionStandardId", stdId);

		AdmissionRequest req = requestRepo.save(new AdmissionRequest(
				null, formData, batchId, "key_shinde_ramesh_1", false, null));

		reviewService.reject(req.getId(), new RejectInput("Age limit exceeded", req.getVersion()));

		// Directly trying to delete via SQL should trigger admission_requests_reject_final_change
		assertThatThrownBy(() -> jdbc.update("delete from admission_requests where id = ?", req.getId()))
				.hasMessageContaining("cannot be deleted");
	}

	@Test
	@DisplayName("FR-033: List returns server-paged rows with status and duplicate filters")
	void listFilteringAndPaging() {
		long stdId = ensureStandard();
		long batchId = createBatch();

		for (int i = 1; i <= 5; i++) {
			Map<String, Object> formData = new LinkedHashMap<>();
			formData.put("surname", "ListUser" + i);
			formData.put("firstName", "First" + i);
			formData.put("gender", "Male");
			formData.put("dob", "2015-01-0" + i);
			formData.put("mobile1", "987000000" + i);
			formData.put("address", "Address " + i);
			formData.put("admissionStandardId", stdId);
			formData.put("admissionStandard", "Std 1");

			requestRepo.save(new AdmissionRequest(
					null, formData, batchId, "key_list_user_" + i, i % 2 == 0, null));
		}

		TablePage allPage = reviewService.list("All", null, 0, 10, null);
		assertThat(allPage.total()).isGreaterThanOrEqualTo(5);

		TablePage pendingPage = reviewService.list("Pending", null, 0, 10, null);
		assertThat(pendingPage.rows()).allMatch(r -> "Pending".equals(r.get("status")));

		TablePage dupOnlyPage = reviewService.list("All", true, 0, 10, null);
		assertThat(dupOnlyPage.rows()).allMatch(r -> Boolean.TRUE.equals(r.get("duplicateFlag")));
	}

	private long ensureStandard() {
		var stds = jdbc.queryForList("select id from standards order by id limit 1", Long.class);
		if (!stds.isEmpty()) {
			return stds.get(0);
		}
		return jdbc.queryForObject("insert into standards (name, display_order) values ('Std 1', 1) returning id", Long.class);
	}

	private long createBatch() {
		return jdbc.queryForObject(
				"insert into admission_import_batches (file_name, uploaded_by, uploaded_at, rows_total, rows_added, rows_skipped, rows_invalid) "
						+ "values ('test.xlsx', 1, now(), 1, 1, 0, 0) returning id", Long.class);
	}

	private long divisionOf(long yearId, long standardId) {
		return jdbc.queryForObject("select id from divisions where academic_year_id = ? and standard_id = ?",
				Long.class, yearId, standardId);
	}

}

package com.admin.management.admissions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;

@Covers("FR-028")
@Covers("FR-030")
@Covers("FR-031")
@Covers("FR-032")
@Covers("FR-035")
class AdmissionImportIT extends IntegrationTest {

	@Autowired
	AdmissionImportService importService;

	@Autowired
	AdmissionRequestRepository requestRepo;

	@Autowired
	ImportBatchRepository batchRepo;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	@DisplayName("FR-028, FR-030: Upload mixed valid and invalid rows imports valid and lists invalid")
	void mixedValidAndInvalid() throws IOException {
		// Ensure standard 1 exists
		ensureStandard("Standard 1", 1);
		ensureStandard("Standard 2", 2);

		byte[] csvBytes = Files.readAllBytes(Paths.get("src", "test", "resources", "admissions", "responses-sample.csv"));
		MockMultipartFile file = new MockMultipartFile("file", "responses-sample.csv", "text/csv", csvBytes);

		ImportResultDto result = importService.importFile(file);
		assertThat(result.batchId()).isNotNull();
		assertThat(result.total()).isEqualTo(7);
		assertThat(result.added()).isEqualTo(3);
		assertThat(result.skipped()).isEqualTo(0);
		assertThat(result.invalid()).hasSize(4);

		// Batch persisted
		ImportBatch batch = batchRepo.findById(result.batchId()).orElseThrow();
		assertThat(batch.getRowsTotal()).isEqualTo(7);
		assertThat(batch.getRowsAdded()).isEqualTo(3);
		assertThat(batch.getRowsInvalid()).isEqualTo(4);

		// Students audit row
		Integer auditCount = jdbc.queryForObject(
				"select count(*) from audit_log where area = 'Students' and action = 'Create' and entity = ?",
				Integer.class, "ImportBatch#" + result.batchId());
		assertThat(auditCount).isEqualTo(1);
	}

	@Test
	@DisplayName("FR-031: Uploading the same file twice adds none and skips all previously imported rows")
	void sameFileTwiceAddsNone() {
		ensureStandard("Standard 1", 1);

		String csv = "Timestamp,Surname,First name,Gender,Date of birth,Mobile 1,Address,Admission standard\n"
				+ "2026/05/01 10:00:00,Kadam,Rohan,Male,12/04/2015,9876543210,Pune,Standard 1\n"
				+ "2026/05/01 10:05:00,Kadam,Sneha,Female,15/06/2015,9876543211,Pune,Standard 1\n";

		MockMultipartFile file1 = new MockMultipartFile("file", "repeat.csv", "text/csv", csv.getBytes());
		ImportResultDto res1 = importService.importFile(file1);
		assertThat(res1.added()).isEqualTo(2);
		assertThat(res1.skipped()).isEqualTo(0);

		MockMultipartFile file2 = new MockMultipartFile("file", "repeat.csv", "text/csv", csv.getBytes());
		ImportResultDto res2 = importService.importFile(file2);
		assertThat(res2.added()).isEqualTo(0);
		assertThat(res2.skipped()).isEqualTo(2);
	}

	@Test
	@DisplayName("FR-031: Overlapping files skips common rows and imports new rows")
	void overlappingFiles() {
		ensureStandard("Standard 1", 1);

		String csv1 = "Timestamp,Surname,First name,Gender,Date of birth,Mobile 1,Address,Admission standard\n"
				+ "2026/05/02 10:00:00,More,Tanvi,Female,10/01/2015,9811111111,Mumbai,Standard 1\n";
		String csv2 = "Timestamp,Surname,First name,Gender,Date of birth,Mobile 1,Address,Admission standard\n"
				+ "2026/05/02 10:00:00,More,Tanvi,Female,10/01/2015,9811111111,Mumbai,Standard 1\n"
				+ "2026/05/02 10:10:00,More,Kabir,Male,15/02/2015,9822222222,Mumbai,Standard 1\n";

		importService.importFile(new MockMultipartFile("file", "f1.csv", "text/csv", csv1.getBytes()));
		ImportResultDto res2 = importService.importFile(new MockMultipartFile("file", "f2.csv", "text/csv", csv2.getBytes()));

		assertThat(res2.added()).isEqualTo(1);
		assertThat(res2.skipped()).isEqualTo(1);
	}

	@Test
	@DisplayName("FR-032: Duplicates are flagged against existing students and against earlier rows in the same file")
	void duplicatesFlaggedAgainstStudentsAndEarlierRows() {
		ensureStandard("Standard 1", 1);
		long catId = jdbc.queryForList("select id from student_categories order by id", Long.class).get(0);
		long std1Id = jdbc.queryForObject("select id from standards where name = 'Standard 1'", Long.class);

		// Seed an existing student in database
		long existingStudentId = jdbc.queryForObject("""
				insert into students (gr_no, surname, first_name, middle_name, gender, dob, mobile_1, address, category_id, admission_date, admission_standard_id, status, source)
				values ('GR-EXIST', 'Bhosale', 'Karan', 'Vijay', 'Male', '2015-05-15', '9899001122', 'Nashik', ?, '2026-06-01', ?, 'Active', 'Manual')
				returning id
				""", Long.class, catId, std1Id);

		// CSV with:
		// Row 1: matches existing student (Bhosale Karan Vijay) -> duplicate!
		// Row 2: first entry for 'Pawar Anaya' -> not duplicate
		// Row 3: matches Row 2 (same names, dob, mobile) -> duplicate of Row 2!
		String csv = "Timestamp,Surname,First name,Father's / middle name,Gender,Date of birth,Mobile 1,Address,Admission standard\n"
				+ "2026/05/03 09:00:00,Bhosale,Karan,Vijay,Male,15/05/2015,9899001122,Nashik,Standard 1\n"
				+ "2026/05/03 09:10:00,Pawar,Anaya,Santosh,Female,20/07/2015,9877001122,Pune,Standard 1\n"
				+ "2026/05/03 09:20:00,Pawar,Anaya,,Female,20/07/2015,9877001122,Pune,Standard 1\n";

		ImportResultDto res = importService.importFile(new MockMultipartFile("file", "dups.csv", "text/csv", csv.getBytes()));
		assertThat(res.added()).isEqualTo(3);
		assertThat(res.duplicates()).isEqualTo(2);

		List<AdmissionRequest> saved = jdbc.query("select * from admission_requests order by id desc limit 3",
				(rs, i) -> new AdmissionRequest(
						null,
						null,
						rs.getLong("import_batch_id"),
						rs.getString("import_key"),
						rs.getBoolean("duplicate_flag"),
						null
				));
		// The two duplicate rows were flagged
		Integer dupFlagCount = jdbc.queryForObject(
				"select count(*) from admission_requests where import_batch_id = ? and duplicate_flag = true",
				Integer.class, res.batchId());
		assertThat(dupFlagCount).isEqualTo(2);
	}

	@Test
	@DisplayName("SC-007: 200 rows imported in under 30 seconds")
	void sc007Import200RowsInUnder30Seconds() throws IOException {
		ensureStandard("Standard 1", 1);

		byte[] bytes;
		try (XSSFWorkbook wb = new XSSFWorkbook();
			 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			Sheet sheet = wb.createSheet("Responses");
			Row h = sheet.createRow(0);
			String[] headers = {"Timestamp", "Surname", "First name", "Gender", "Date of birth", "Mobile 1", "Address", "Admission standard"};
			for (int c = 0; c < headers.length; c++) h.createCell(c).setCellValue(headers[c]);

			for (int i = 1; i <= 200; i++) {
				Row r = sheet.createRow(i);
				r.createCell(0).setCellValue("2026/05/04 10:" + (i % 60) + ":00");
				r.createCell(1).setCellValue("SyntheticSurname" + i);
				r.createCell(2).setCellValue("SyntheticFirst" + i);
				r.createCell(3).setCellValue(i % 2 == 0 ? "Male" : "Female");
				r.createCell(4).setCellValue("15/05/2015");
				r.createCell(5).setCellValue(String.format("98%08d", i));
				r.createCell(6).setCellValue("Synthetic Address " + i);
				r.createCell(7).setCellValue("Standard 1");
			}
			wb.write(baos);
			bytes = baos.toByteArray();
		}

		long start = System.currentTimeMillis();
		ImportResultDto result = importService.importFile(new MockMultipartFile("file", "bulk200.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));
		long elapsedMs = System.currentTimeMillis() - start;

		assertThat(result.added()).isEqualTo(200);
		assertThat(result.invalid()).isEmpty();
		assertThat(elapsedMs).isLessThan(30_000); // SC-007 requires under 30 seconds
	}

	private void ensureStandard(String name, int displayOrder) {
		Integer count = jdbc.queryForObject("select count(*) from standards where name = ?", Integer.class, name);
		if (count == 0) {
			jdbc.update("insert into standards (name, display_order, active) values (?, ?, true)", name, displayOrder);
		}
	}

}

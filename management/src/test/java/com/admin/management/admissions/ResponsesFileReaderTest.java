package com.admin.management.admissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.admin.management.admissions.ResponsesFileReader.InvalidRow;
import com.admin.management.admissions.ResponsesFileReader.ParsedAdmissionRow;
import com.admin.management.admissions.ResponsesFileReader.ReadResult;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.support.Covers;

@Covers("FR-028")
@Covers("FR-029")
@Covers("FR-030")
@Covers("FR-040")
class ResponsesFileReaderTest {

	private static final Clock FIXED_CLOCK = Clock.fixed(
			Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("Asia/Kolkata"));

	private final ResponsesFileReader reader = new ResponsesFileReader(FIXED_CLOCK);

	private final Map<String, Long> standardMap = Map.of(
			"Standard 1", 1L,
			"Standard 2", 2L,
			"Standard 10", 10L,
			"Standard 11", 11L,
			"Standard 12", 12L
	);

	@BeforeAll
	static void ensureSampleXlsxExists() throws IOException {
		Path dir = Paths.get("src", "test", "resources", "admissions");
		Files.createDirectories(dir);
		Path xlsxPath = dir.resolve("responses-sample.xlsx");
		if (!Files.exists(xlsxPath)) {
			try (XSSFWorkbook wb = new XSSFWorkbook();
				 FileOutputStream fos = new FileOutputStream(xlsxPath.toFile())) {
				Sheet sheet = wb.createSheet("Responses");
				String[] headers = {
						"Timestamp", "Surname", "First name", "Father's / middle name", "Gender",
						"Date of birth", "Place of birth", "Father's name", "Mother's name",
						"Mobile 1", "Mobile 2", "Address", "Aadhaar number", "Religion / Caste",
						"Category", "Previous school", "Admission date", "Admission standard"
				};
				Row headerRow = sheet.createRow(0);
				for (int i = 0; i < headers.length; i++) {
					headerRow.createCell(i).setCellValue(headers[i]);
				}

				String[][] rows = {
						{"2026/05/01 09:30:00", "Verma", "Rohan", "Anil", "Male", "12/04/2015", "Pune", "Anil Verma", "Sunita Verma", "9876543210", "9876543211", "Flat 101, Green Acres, Pune", "123456789012", "Hindu", "General", "St Mary School", "01/06/2026", "Standard 1"},
						{"2026/05/01 10:15:00", "Verma", "Rohan", "", "Male", "12/04/2015", "Pune", "Anil Verma", "Sunita Verma", "9876543210", "", "Flat 101 Green Acres", "123456789012", "Hindu", "General", "", "01/06/2026", "Standard 1"},
						{"2026/05/01 10:20:00", "Verma", "Vivaan", "Anil", "Male", "12/04/2015", "Pune", "Anil Verma", "Sunita Verma", "9876543210", "", "Flat 101 Green Acres", "123456789013", "Hindu", "General", "", "01/06/2026", "Standard 1"},
						{"2026/05/01 11:00:00", "", "Aarav", "Suresh", "Male", "15/06/2014", "Mumbai", "Suresh", "Meena", "9822334455", "", "45 MG Road, Mumbai", "", "Hindu", "OBC", "", "", "Standard 2"},
						{"2026/05/01 11:30:00", "Patil", "Pooja", "Rajesh", "Female", "20/08/2015", "Nashik", "Rajesh", "Kavita", "98223344", "", "12 Station Road, Nashik", "", "Hindu", "General", "", "", "Standard 1"},
						{"2026/05/01 12:00:00", "Joshi", "Neha", "Mahesh", "Female", "15/08/2030", "Thane", "Mahesh", "Rekha", "9833445566", "", "78 Lake View, Thane", "", "Hindu", "General", "", "", "Standard 1"},
						{"2026/05/01 12:30:00", "Kulkarni", "Omkar", "Sanjay", "Male", "10/10/2014", "Nagpur", "Sanjay", "Asha", "9844556677", "", "88 Ring Road, Nagpur", "", "Hindu", "General", "", "", "Standard 99"}
				};

				for (int r = 0; r < rows.length; r++) {
					Row row = sheet.createRow(r + 1);
					for (int c = 0; c < rows[r].length; c++) {
						row.createCell(c).setCellValue(rows[r][c]);
					}
				}
				wb.write(fos);
			}
		}
	}

	@Test
	@DisplayName("FR-028, FR-040: Reads synthetic responses-sample.csv with valid and invalid rows")
	void readsSampleCsvFile() throws IOException {
		byte[] bytes = Files.readAllBytes(Paths.get("src", "test", "resources", "admissions", "responses-sample.csv"));
		ReadResult result = reader.read(bytes, "responses-sample.csv", standardMap::get);

		assertThat(result.validRows()).hasSize(3);
		assertThat(result.invalidRows()).hasSize(4);

		ParsedAdmissionRow first = result.validRows().get(0);
		assertThat(first.surname()).isEqualTo("Verma");
		assertThat(first.firstName()).isEqualTo("Rohan");
		assertThat(first.gender()).isEqualTo("Male");
		assertThat(first.dob()).isEqualTo(LocalDate.of(2015, 4, 12));
		assertThat(first.mobile1()).isEqualTo("9876543210");
		assertThat(first.address()).isEqualTo("Flat 101, Green Acres, Pune");
		assertThat(first.standardName()).isEqualTo("Standard 1");
		assertThat(first.standardId()).isEqualTo(1L);

		// Invalid rows check: rows 5, 6, 7, 8
		List<InvalidRow> invalid = result.invalidRows();
		assertThat(invalid.get(0).row()).isEqualTo(5); // row 5: missing surname
		assertThat(invalid.get(0).reason()).contains("surname");

		assertThat(invalid.get(1).row()).isEqualTo(6); // row 6: 8-digit mobile
		assertThat(invalid.get(1).reason()).contains("Mobile 1");

		assertThat(invalid.get(2).row()).isEqualTo(7); // row 7: future DOB
		assertThat(invalid.get(2).reason()).contains("date of birth");

		assertThat(invalid.get(3).row()).isEqualTo(8); // row 8: unknown standard
		assertThat(invalid.get(3).reason()).contains("Admission standard");
	}

	@Test
	@DisplayName("FR-028, FR-040: Reads synthetic responses-sample.xlsx with valid and invalid rows")
	void readsSampleXlsxFile() throws IOException {
		byte[] bytes = Files.readAllBytes(Paths.get("src", "test", "resources", "admissions", "responses-sample.xlsx"));
		ReadResult result = reader.read(bytes, "responses-sample.xlsx", standardMap::get);

		assertThat(result.validRows()).hasSize(3);
		assertThat(result.invalidRows()).hasSize(4);
	}

	@Test
	@DisplayName("FR-029: Missing required header refuses whole file with MISSING_COLUMNS")
	void missingRequiredHeadersRefusesWholeFile() {
		String csvMissingMobileAndAddress = "Surname,First name,Gender,Date of birth,Admission standard\n"
				+ "Sharma,Aarav,Male,15/05/2015,Standard 1\n";

		assertThatThrownBy(() -> reader.read(csvMissingMobileAndAddress.getBytes(), "test.csv", standardMap::get))
				.isInstanceOf(ValidationException.class)
				.satisfies(ex -> {
					ValidationException ve = (ValidationException) ex;
					assertThat(ve.getCode()).isEqualTo("MISSING_COLUMNS");
					assertThat(ve.getFieldErrors()).anySatisfy(fe -> {
						assertThat(fe.field()).isEqualTo("file");
						assertThat(fe.message()).contains("Mobile 1", "Address");
					});
				});
	}

	@Test
	@DisplayName("FR-028: Formula cells in Excel are read as cached values and never evaluated")
	void formulaCellsReadAsCachedValues() throws IOException {
		byte[] bytes;
		try (XSSFWorkbook wb = new XSSFWorkbook();
			 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			Sheet sheet = wb.createSheet();
			Row h = sheet.createRow(0);
			h.createCell(0).setCellValue("Surname");
			h.createCell(1).setCellValue("First name");
			h.createCell(2).setCellValue("Gender");
			h.createCell(3).setCellValue("Date of birth");
			h.createCell(4).setCellValue("Mobile 1");
			h.createCell(5).setCellValue("Address");
			h.createCell(6).setCellValue("Admission standard");

			Row r = sheet.createRow(1);
			r.createCell(0).setCellValue("Gupta");
			// First name cell has formula "UPPER(\"Aman\")" but pre-cached string value "Aman"
			Cell formulaCell = r.createCell(1);
			formulaCell.setCellFormula("UPPER(\"Aman\")");
			formulaCell.setCellValue("Aman"); // cached value

			r.createCell(2).setCellValue("Male");
			r.createCell(3).setCellValue("2015-05-10");
			r.createCell(4).setCellValue("9876543210");
			r.createCell(5).setCellValue("Station Road, Mumbai");
			r.createCell(6).setCellValue("Standard 1");

			wb.write(baos);
			bytes = baos.toByteArray();
		}

		ReadResult result = reader.read(bytes, "formula.xlsx", standardMap::get);
		assertThat(result.validRows()).hasSize(1);
		assertThat(result.validRows().get(0).firstName()).isEqualTo("Aman");
	}

	@Test
	@DisplayName("FR-028: File exceeding 5 MB is rejected with 422")
	void fileLargerThan5MbRejected() {
		byte[] largeBytes = new byte[(int) (ResponsesFileReader.MAX_FILE_SIZE_BYTES + 10)];
		assertThatThrownBy(() -> reader.read(largeBytes, "large.csv", standardMap::get))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("Validation failed")
				.satisfies(ex -> {
					ValidationException ve = (ValidationException) ex;
					assertThat(ve.getFieldErrors()).anySatisfy(fe -> {
						assertThat(fe.field()).isEqualTo("file");
						assertThat(fe.message()).contains("5 MB");
					});
				});
	}

	@Test
	@DisplayName("FR-028: File exceeding 2,000 data rows is rejected with 422")
	void fileWithMoreThan2000RowsRejected() {
		StringBuilder sb = new StringBuilder();
		sb.append("Surname,First name,Gender,Date of birth,Mobile 1,Address,Admission standard\n");
		for (int i = 0; i < 2001; i++) {
			sb.append("Sharma,Student").append(i).append(",Male,15/05/2015,9876543210,Pune,Standard 1\n");
		}

		assertThatThrownBy(() -> reader.read(sb.toString().getBytes(), "rows.csv", standardMap::get))
				.isInstanceOf(ValidationException.class)
				.satisfies(ex -> {
					ValidationException ve = (ValidationException) ex;
					assertThat(ve.getFieldErrors()).anySatisfy(fe -> {
						assertThat(fe.field()).isEqualTo("file");
						assertThat(fe.message()).contains("2,000 rows");
					});
				});
	}

	@Test
	@DisplayName("FR-028: Unsupported file type is rejected with 422")
	void unsupportedFileTypeRejected() {
		byte[] invalidBytes = new byte[]{0x00, 0x01, 0x02, 0x03}; // non-zip binary
		assertThatThrownBy(() -> reader.read(invalidBytes, "unknown.bin", standardMap::get))
				.isInstanceOf(ValidationException.class)
				.satisfies(ex -> {
					ValidationException ve = (ValidationException) ex;
					assertThat(ve.getFieldErrors()).anySatisfy(fe -> {
						assertThat(fe.field()).isEqualTo("file");
						assertThat(fe.message()).contains("Excel (.xlsx) or CSV (.csv)");
					});
				});
	}

}

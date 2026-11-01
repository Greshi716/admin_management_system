package com.admin.management.admissions;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import com.admin.management.shared.web.ApiProblem.FieldError;
import com.admin.management.shared.web.ValidationException;

/**
 * Reads Google Form responses from Excel (.xlsx) and CSV (.csv) files (FR-028, FR-029, FR-030, FR-040).
 *
 * <p>Limits: 5 MB file size and 2,000 data rows.
 * Formula cells are read as cached values and never evaluated.
 */
@Component
public class ResponsesFileReader {

	public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
	public static final int MAX_DATA_ROWS = 2000;

	private static final Pattern TEN_DIGITS = Pattern.compile("^\\d{10}$");
	private static final Pattern TWELVE_DIGITS = Pattern.compile("^\\d{12}$");
	private static final Set<String> ALLOWED_GENDERS = Set.of("Male", "Female", "Other");

	private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
			DateTimeFormatter.ofPattern("dd/MM/yyyy"),
			DateTimeFormatter.ofPattern("d/M/yyyy"),
			DateTimeFormatter.ofPattern("dd-MM-yyyy"),
			DateTimeFormatter.ofPattern("d-M-yyyy"),
			DateTimeFormatter.ofPattern("yyyy-MM-dd"),
			DateTimeFormatter.ISO_LOCAL_DATE
	);

	// Canonical required header names
	public static final String H_SURNAME = "Surname";
	public static final String H_FIRST_NAME = "First name";
	public static final String H_GENDER = "Gender";
	public static final String H_DOB = "Date of birth";
	public static final String H_MOBILE_1 = "Mobile 1";
	public static final String H_ADDRESS = "Address";
	public static final String H_ADMISSION_STANDARD = "Admission standard";

	// Canonical optional header names
	public static final String H_TIMESTAMP = "Timestamp";
	public static final String H_MIDDLE_NAME = "Father's / middle name";
	public static final String H_BIRTH_PLACE = "Place of birth";
	public static final String H_FATHER_NAME = "Father's name";
	public static final String H_MOTHER_NAME = "Mother's name";
	public static final String H_MOBILE_2 = "Mobile 2";
	public static final String H_AADHAAR = "Aadhaar number";
	public static final String H_RELIGION_CASTE = "Religion / Caste";
	public static final String H_CATEGORY = "Category";
	public static final String H_PREVIOUS_SCHOOL = "Previous school";
	public static final String H_ADMISSION_DATE = "Admission date";

	private static final List<String> REQUIRED_HEADERS = List.of(
			H_SURNAME, H_FIRST_NAME, H_GENDER, H_DOB, H_MOBILE_1, H_ADDRESS, H_ADMISSION_STANDARD
	);

	private final Clock clock;

	public ResponsesFileReader(Clock clock) {
		this.clock = clock;
	}

	public record InvalidRow(int row, String reason) {
	}

	public record ParsedAdmissionRow(
			int rowNumber,
			Instant submittedAt,
			String rawTimestamp,
			String surname,
			String firstName,
			String middleName,
			String gender,
			LocalDate dob,
			String birthPlace,
			String fatherName,
			String motherName,
			String mobile1,
			String mobile2,
			String address,
			String aadhaar,
			String religionCaste,
			String category,
			String previousSchool,
			LocalDate admissionDate,
			String standardName,
			Long standardId,
			Map<String, Object> formData,
			String importKey
	) {
	}

	public record ReadResult(List<ParsedAdmissionRow> validRows, List<InvalidRow> invalidRows) {
	}

	/**
	 * Reads an uploaded responses file (.xlsx or .csv) and validates its rows.
	 *
	 * @param bytes               the file content
	 * @param originalFilename    the original file name
	 * @param standardNameToIdMap lookup function or map for active standards
	 * @return valid and invalid rows
	 */
	public ReadResult read(byte[] bytes, String originalFilename,
			Function<String, Long> standardNameToIdMap) {
		if (bytes == null || bytes.length == 0) {
			throw new ValidationException(List.of(new FieldError("file", "Choose a file to upload.")));
		}
		if (bytes.length > MAX_FILE_SIZE_BYTES) {
			throw new ValidationException(List.of(new FieldError("file", "The file is larger than 5 MB.")));
		}

		String lowerName = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
		boolean isXlsx = lowerName.endsWith(".xlsx") || isZipHeader(bytes);
		boolean isCsv = lowerName.endsWith(".csv") || (!isXlsx && isText(bytes));

		if (!isXlsx && !isCsv) {
			throw new ValidationException(List.of(
					new FieldError("file", "The file must be an Excel (.xlsx) or CSV (.csv) file.")));
		}

		List<List<String>> rawRows = isXlsx ? readXlsxRows(bytes) : readCsvRows(bytes);

		if (rawRows.isEmpty()) {
			throw new ValidationException("MISSING_COLUMNS", List.of(
					new FieldError("file", "The file is empty. Missing required columns: " + String.join(", ", REQUIRED_HEADERS))));
		}

		// First non-empty row is header
		int headerRowIndex = 0;
		while (headerRowIndex < rawRows.size() && isRowEmpty(rawRows.get(headerRowIndex))) {
			headerRowIndex++;
		}
		if (headerRowIndex >= rawRows.size()) {
			throw new ValidationException("MISSING_COLUMNS", List.of(
					new FieldError("file", "The file is empty. Missing required columns: " + String.join(", ", REQUIRED_HEADERS))));
		}

		List<String> headerRow = rawRows.get(headerRowIndex);
		Map<String, Integer> headerIndices = mapHeaders(headerRow);

		// Check required headers
		List<String> missingRequired = new ArrayList<>();
		for (String req : REQUIRED_HEADERS) {
			if (!headerIndices.containsKey(req)) {
				missingRequired.add(req);
			}
		}

		if (!missingRequired.isEmpty()) {
			throw new ValidationException("MISSING_COLUMNS", List.of(
					new FieldError("file", "Missing required columns: " + String.join(", ", missingRequired))));
		}

		List<ParsedAdmissionRow> validRows = new ArrayList<>();
		List<InvalidRow> invalidRows = new ArrayList<>();

		LocalDate today = LocalDate.now(clock);
		int dataRowCount = 0;

		for (int i = headerRowIndex + 1; i < rawRows.size(); i++) {
			List<String> rowValues = rawRows.get(i);
			if (isRowEmpty(rowValues)) {
				continue;
			}
			dataRowCount++;
			if (dataRowCount > MAX_DATA_ROWS) {
				throw new ValidationException(List.of(
						new FieldError("file", "The file has more than 2,000 rows.")));
			}

			int rowNumber = i + 1; // 1-based row number matching spreadsheet
			processRow(rowNumber, rowValues, headerIndices, standardNameToIdMap, today, validRows, invalidRows);
		}

		return new ReadResult(Collections.unmodifiableList(validRows), Collections.unmodifiableList(invalidRows));
	}

	private void processRow(
			int rowNumber,
			List<String> rowValues,
			Map<String, Integer> headerIndices,
			Function<String, Long> standardNameToIdMap,
			LocalDate today,
			List<ParsedAdmissionRow> validRows,
			List<InvalidRow> invalidRows
	) {
		String surname = getCell(rowValues, headerIndices.get(H_SURNAME));
		String firstName = getCell(rowValues, headerIndices.get(H_FIRST_NAME));
		String rawGender = getCell(rowValues, headerIndices.get(H_GENDER));
		String rawDob = getCell(rowValues, headerIndices.get(H_DOB));
		String rawMobile1 = getCell(rowValues, headerIndices.get(H_MOBILE_1));
		String address = getCell(rowValues, headerIndices.get(H_ADDRESS));
		String rawStandard = getCell(rowValues, headerIndices.get(H_ADMISSION_STANDARD));

		List<String> reasons = new ArrayList<>();

		if (surname == null) {
			reasons.add("Enter the surname.");
		}
		if (firstName == null) {
			reasons.add("Enter the first name.");
		}

		String gender = normalizeGender(rawGender);
		if (gender == null) {
			reasons.add("Choose Male, Female or Other.");
		}

		LocalDate dob = parseDate(rawDob);
		if (dob == null) {
			reasons.add("Enter a valid date of birth.");
		} else if (dob.isAfter(today)) {
			reasons.add("The date of birth cannot be after today.");
		}

		String mobile1 = cleanDigits(rawMobile1);
		if (mobile1 == null || !TEN_DIGITS.matcher(mobile1).matches()) {
			reasons.add("Mobile 1 must be a 10-digit number.");
		}

		if (address == null) {
			reasons.add("Enter the address.");
		}

		Long standardId = null;
		if (rawStandard == null) {
			reasons.add("Choose an active standard.");
		} else {
			standardId = standardNameToIdMap != null ? standardNameToIdMap.apply(rawStandard) : null;
			if (standardId == null) {
				reasons.add("Admission standard does not match an active standard: " + rawStandard);
			}
		}

		if (!reasons.isEmpty()) {
			invalidRows.add(new InvalidRow(rowNumber, String.join(" ", reasons)));
			return;
		}

		// Optional fields
		String rawTimestamp = getCell(rowValues, headerIndices.get(H_TIMESTAMP));
		Instant submittedAt = parseTimestamp(rawTimestamp);
		String middleName = getCell(rowValues, headerIndices.get(H_MIDDLE_NAME));
		String birthPlace = getCell(rowValues, headerIndices.get(H_BIRTH_PLACE));
		String fatherName = getCell(rowValues, headerIndices.get(H_FATHER_NAME));
		String motherName = getCell(rowValues, headerIndices.get(H_MOTHER_NAME));
		String rawMobile2 = getCell(rowValues, headerIndices.get(H_MOBILE_2));
		String mobile2 = cleanDigits(rawMobile2);
		String rawAadhaar = getCell(rowValues, headerIndices.get(H_AADHAAR));
		String aadhaar = cleanDigits(rawAadhaar);
		if (aadhaar != null && !TWELVE_DIGITS.matcher(aadhaar).matches()) {
			// Leave as raw digits or null if malformed
			aadhaar = null;
		}
		String religionCaste = getCell(rowValues, headerIndices.get(H_RELIGION_CASTE));
		String category = getCell(rowValues, headerIndices.get(H_CATEGORY));
		String previousSchool = getCell(rowValues, headerIndices.get(H_PREVIOUS_SCHOOL));
		String rawAdmissionDate = getCell(rowValues, headerIndices.get(H_ADMISSION_DATE));
		LocalDate admissionDate = parseDate(rawAdmissionDate);

		// Build form_data JSON structure
		Map<String, Object> formData = new LinkedHashMap<>();
		formData.put("surname", surname);
		formData.put("firstName", firstName);
		if (middleName != null) formData.put("middleName", middleName);
		formData.put("gender", gender);
		formData.put("dob", dob.toString());
		if (birthPlace != null) formData.put("birthPlace", birthPlace);
		if (fatherName != null) formData.put("fatherName", fatherName);
		if (motherName != null) formData.put("motherName", motherName);
		formData.put("mobile1", mobile1);
		if (mobile2 != null) formData.put("mobile2", mobile2);
		formData.put("address", address);
		if (aadhaar != null) formData.put("aadhaar", aadhaar);
		if (religionCaste != null) formData.put("religionCaste", religionCaste);
		if (category != null) formData.put("category", category);
		if (previousSchool != null) formData.put("previousSchool", previousSchool);
		if (admissionDate != null) formData.put("admissionDate", admissionDate.toString());
		formData.put("admissionStandard", rawStandard);
		formData.put("admissionStandardId", standardId);
		if (rawTimestamp != null) formData.put("timestamp", rawTimestamp);

		String importKey = DuplicateRule.computeImportKey(rawTimestamp, surname, firstName, middleName, dob);

		validRows.add(new ParsedAdmissionRow(
				rowNumber,
				submittedAt,
				rawTimestamp,
				surname,
				firstName,
				middleName,
				gender,
				dob,
				birthPlace,
				fatherName,
				motherName,
				mobile1,
				mobile2,
				address,
				aadhaar,
				religionCaste,
				category,
				previousSchool,
				admissionDate,
				rawStandard,
				standardId,
				formData,
				importKey
		));
	}

	private static Map<String, Integer> mapHeaders(List<String> headerRow) {
		Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < headerRow.size(); i++) {
			String raw = headerRow.get(i);
			if (raw == null) continue;
			String canonical = normalizeHeader(raw);
			if (canonical != null && !map.containsKey(canonical)) {
				map.put(canonical, i);
			}
		}
		return map;
	}

	private static String normalizeHeader(String raw) {
		String trimmed = raw.trim().toLowerCase(Locale.ROOT);
		String compacted = trimmed.replaceAll("[\\s/_-]+", " ");

		if (compacted.contains("surname") || compacted.equals("last name") || compacted.equals("lastname")) {
			return H_SURNAME;
		}
		if (compacted.equals("first name") || compacted.equals("firstname")) {
			return H_FIRST_NAME;
		}
		if (compacted.equals("gender") || compacted.equals("sex")) {
			return H_GENDER;
		}
		if (compacted.contains("date of birth") || compacted.equals("dob") || compacted.contains("birth date")) {
			return H_DOB;
		}
		if (compacted.contains("mobile 1") || compacted.equals("mobile") || compacted.equals("mobile number")
				|| compacted.equals("contact number") || compacted.equals("phone 1")) {
			return H_MOBILE_1;
		}
		if (compacted.equals("address") || compacted.contains("residential address")) {
			return H_ADDRESS;
		}
		if (compacted.contains("admission standard") || compacted.equals("standard") || compacted.contains("class")) {
			return H_ADMISSION_STANDARD;
		}
		if (compacted.contains("timestamp") || compacted.equals("submission time")) {
			return H_TIMESTAMP;
		}
		if (compacted.contains("middle name") || compacted.contains("father's / middle name")) {
			return H_MIDDLE_NAME;
		}
		if (compacted.contains("place of birth") || compacted.contains("birth place")) {
			return H_BIRTH_PLACE;
		}
		if (compacted.contains("father's name") || compacted.contains("father name")) {
			return H_FATHER_NAME;
		}
		if (compacted.contains("mother's name") || compacted.contains("mother name")) {
			return H_MOTHER_NAME;
		}
		if (compacted.contains("mobile 2") || compacted.contains("phone 2") || compacted.contains("alternate mobile")) {
			return H_MOBILE_2;
		}
		if (compacted.contains("aadhaar") || compacted.contains("aadhar")) {
			return H_AADHAAR;
		}
		if (compacted.contains("religion") || compacted.contains("caste")) {
			return H_RELIGION_CASTE;
		}
		if (compacted.contains("category")) {
			return H_CATEGORY;
		}
		if (compacted.contains("previous school") || compacted.contains("last school")) {
			return H_PREVIOUS_SCHOOL;
		}
		if (compacted.contains("admission date")) {
			return H_ADMISSION_DATE;
		}

		return null;
	}

	private static String getCell(List<String> row, Integer colIndex) {
		if (colIndex == null || colIndex < 0 || colIndex >= row.size()) {
			return null;
		}
		String val = row.get(colIndex);
		if (val == null) {
			return null;
		}
		String trimmed = val.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String normalizeGender(String raw) {
		if (raw == null) return null;
		String trimmed = raw.trim().toLowerCase(Locale.ROOT);
		if (trimmed.equals("male") || trimmed.equals("boy") || trimmed.equals("m")) return "Male";
		if (trimmed.equals("female") || trimmed.equals("girl") || trimmed.equals("f")) return "Female";
		if (trimmed.equals("other") || trimmed.equals("o")) return "Other";
		return null;
	}

	private static LocalDate parseDate(String raw) {
		if (raw == null) return null;
		String trimmed = raw.trim();
		// If date has time part (e.g. 2015-05-10 00:00:00 or ISO)
		if (trimmed.contains("T")) {
			try {
				return LocalDate.parse(trimmed.substring(0, trimmed.indexOf('T')));
			} catch (Exception ignored) {
			}
		}
		if (trimmed.contains(" ")) {
			String datePart = trimmed.substring(0, trimmed.indexOf(' '));
			LocalDate d = tryParseDate(datePart);
			if (d != null) return d;
		}
		return tryParseDate(trimmed);
	}

	private static LocalDate tryParseDate(String dateStr) {
		for (DateTimeFormatter fmt : DATE_FORMATTERS) {
			try {
				return LocalDate.parse(dateStr, fmt);
			} catch (DateTimeParseException ignored) {
			}
		}
		return null;
	}

	private static Instant parseTimestamp(String raw) {
		if (raw == null) return null;
		String trimmed = raw.trim();
		try {
			return Instant.parse(trimmed);
		} catch (Exception ignored) {
		}
		// Try format: yyyy/MM/dd HH:mm:ss, dd/MM/yyyy HH:mm:ss, etc.
		for (String pattern : List.of(
				"yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy HH:mm:ss", "dd-MM-yyyy HH:mm:ss",
				"M/d/yyyy H:mm:ss", "MM/dd/yyyy HH:mm:ss", "d/M/yyyy H:mm:ss"
		)) {
			try {
				LocalDateTime ldt = LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern(pattern));
				return ldt.atZone(ZoneId.systemDefault()).toInstant();
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	private static String cleanDigits(String raw) {
		if (raw == null) return null;
		String digits = raw.replaceAll("\\D", "");
		return digits.isEmpty() ? null : digits;
	}

	private static boolean isRowEmpty(List<String> row) {
		if (row == null || row.isEmpty()) return true;
		for (String c : row) {
			if (c != null && !c.trim().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static List<List<String>> readXlsxRows(byte[] bytes) {
		try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
			if (wb.getNumberOfSheets() == 0) {
				return List.of();
			}
			Sheet sheet = wb.getSheetAt(0);
			List<List<String>> rows = new ArrayList<>();
			int lastRowNum = sheet.getLastRowNum();
			for (int r = 0; r <= lastRowNum; r++) {
				Row row = sheet.getRow(r);
				if (row == null) {
					rows.add(List.of());
					continue;
				}
				short lastCellNum = row.getLastCellNum();
				if (lastCellNum < 0) {
					rows.add(List.of());
					continue;
				}
				List<String> rowCells = new ArrayList<>(lastCellNum);
				for (int c = 0; c < lastCellNum; c++) {
					Cell cell = row.getCell(c);
					rowCells.add(readCellValue(cell));
				}
				rows.add(rowCells);
			}
			return rows;
		} catch (IOException e) {
			throw new ValidationException(List.of(
					new FieldError("file", "Could not read Excel file: " + e.getMessage())));
		}
	}

	private static String readCellValue(Cell cell) {
		if (cell == null) return null;
		CellType cellType = cell.getCellType();

		// Handle formula without evaluating: read cached formula result type
		if (cellType == CellType.FORMULA) {
			cellType = cell.getCachedFormulaResultType();
		}

		return switch (cellType) {
			case STRING -> cell.getStringCellValue();
			case NUMERIC -> {
				if (DateUtil.isCellDateFormatted(cell)) {
					try {
						LocalDateTime ldt = cell.getLocalDateTimeCellValue();
						yield ldt.toLocalDate().toString();
					} catch (Exception e) {
						yield cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
					}
				}
				double num = cell.getNumericCellValue();
				// If integer, strip trailing .0
				if (num == Math.floor(num) && !Double.isInfinite(num)) {
					yield String.valueOf((long) num);
				}
				yield String.valueOf(num);
			}
			case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
			case BLANK, _NONE, ERROR -> null;
			default -> null;
		};
	}

	/**
	 * Simple in-project RFC 4180 CSV parser.
	 */
	static List<List<String>> readCsvRows(byte[] bytes) {
		String text = new String(bytes, StandardCharsets.UTF_8);
		// Strip UTF-8 BOM if present
		if (text.startsWith("\uFEFF")) {
			text = text.substring(1);
		}

		List<List<String>> rows = new ArrayList<>();
		List<String> currentRow = new ArrayList<>();
		StringBuilder currentField = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);

			if (inQuotes) {
				if (c == '"') {
					if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
						currentField.append('"');
						i++; // Skip escaped quote
					} else {
						inQuotes = false;
					}
				} else {
					currentField.append(c);
				}
			} else {
				if (c == '"') {
					inQuotes = true;
				} else if (c == ',') {
					currentRow.add(currentField.toString());
					currentField.setLength(0);
				} else if (c == '\r') {
					// Check for CRLF
					if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
						i++;
					}
					currentRow.add(currentField.toString());
					currentField.setLength(0);
					rows.add(new ArrayList<>(currentRow));
					currentRow.clear();
				} else if (c == '\n') {
					currentRow.add(currentField.toString());
					currentField.setLength(0);
					rows.add(new ArrayList<>(currentRow));
					currentRow.clear();
				} else {
					currentField.append(c);
				}
			}
		}

		// Trailing field/row
		if (currentField.length() > 0 || !currentRow.isEmpty()) {
			currentRow.add(currentField.toString());
			rows.add(currentRow);
		}

		return rows;
	}

	private static boolean isZipHeader(byte[] bytes) {
		return bytes != null && bytes.length >= 4
				&& bytes[0] == 0x50 && bytes[1] == 0x4B
				&& bytes[2] == 0x03 && bytes[3] == 0x04;
	}

	private static boolean isText(byte[] bytes) {
		if (bytes == null || bytes.length == 0) return false;
		int limit = Math.min(bytes.length, 512);
		for (int i = 0; i < limit; i++) {
			byte b = bytes[i];
			if (b == 0) return false; // NUL byte means binary
		}
		return true;
	}

}

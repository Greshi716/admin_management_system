package com.admin.management.admissions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.admin.management.admissions.DuplicateRule.DuplicateResult;
import com.admin.management.admissions.DuplicateRule.PersonCandidate;
import com.admin.management.admissions.ResponsesFileReader.ParsedAdmissionRow;
import com.admin.management.admissions.ResponsesFileReader.ReadResult;
import com.admin.management.audit.AuditService;
import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.shared.CurrentUserProvider;
import com.admin.management.students.api.StudentLookup;

/**
 * Handles uploading and importing Google Form responses files (FR-028, FR-030, FR-031, FR-032, FR-035).
 */
@Service
public class AdmissionImportService {

	private final ResponsesFileReader reader;
	private final ImportBatchRepository batchRepo;
	private final AdmissionRequestRepository requestRepo;
	private final MasterDataLookup masterData;
	private final StudentLookup studentLookup;
	private final AuditService audit;
	private final CurrentUserProvider currentUser;

	public AdmissionImportService(ResponsesFileReader reader, ImportBatchRepository batchRepo,
			AdmissionRequestRepository requestRepo, MasterDataLookup masterData,
			StudentLookup studentLookup, AuditService audit, CurrentUserProvider currentUser) {
		this.reader = reader;
		this.batchRepo = batchRepo;
		this.requestRepo = requestRepo;
		this.masterData = masterData;
		this.studentLookup = studentLookup;
		this.audit = audit;
		this.currentUser = currentUser;
	}

	@Transactional
	public ImportResultDto importFile(MultipartFile file) {
		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (Exception e) {
			throw new RuntimeException("Could not read uploaded file: " + e.getMessage(), e);
		}
		return importBytes(bytes, file.getOriginalFilename());
	}

	@Transactional
	public ImportResultDto importBytes(byte[] bytes, String fileName) {
		Long userId = currentUser.currentUserId().orElse(1L);

		// Active standards map: lowercased trimmed name -> standardId
		Map<String, Long> activeStandardMap = masterData.filterOptions().standards().stream()
				.filter(MasterDataLookup.StandardOption::active)
				.collect(Collectors.toMap(
						s -> s.name().trim().toLowerCase(Locale.ROOT),
						MasterDataLookup.StandardOption::id,
						(existing, replacement) -> existing
				));

		ReadResult readResult = reader.read(bytes, fileName,
				rawName -> rawName == null ? null : activeStandardMap.get(rawName.trim().toLowerCase(Locale.ROOT)));

		List<ParsedAdmissionRow> validRows = readResult.validRows();
		int invalidCount = readResult.invalidRows().size();
		int totalRows = validRows.size() + invalidCount;

		// Initial batch row
		ImportBatch batch = batchRepo.save(new ImportBatch(
				fileName != null ? fileName : "responses.xlsx",
				userId,
				totalRows,
				0,
				0,
				invalidCount
		));

		// Candidates for duplicate checking
		List<PersonCandidate> studentCandidates = studentLookup.duplicateCandidates().stream()
				.map(s -> new PersonCandidate(s.id(), s.surname(), s.firstName(), s.middleName(), s.dob(), s.mobile1()))
				.collect(Collectors.toCollection(ArrayList::new));

		List<PersonCandidate> requestCandidates = requestRepo.findPendingOrApproved().stream()
				.map(AdmissionImportService::toCandidate)
				.collect(Collectors.toCollection(ArrayList::new));

		Set<String> seenImportKeysInBatch = new HashSet<>();
		int addedCount = 0;
		int skippedCount = 0;
		int duplicateCount = 0;

		for (ParsedAdmissionRow row : validRows) {
			String importKey = row.importKey();
			if (seenImportKeysInBatch.contains(importKey) || requestRepo.findByImportKey(importKey).isPresent()) {
				skippedCount++;
				continue;
			}
			seenImportKeysInBatch.add(importKey);

			PersonCandidate rowCandidate = new PersonCandidate(
					null, row.surname(), row.firstName(), row.middleName(), row.dob(), row.mobile1());

			DuplicateResult dupResult = DuplicateRule.checkDuplicates(rowCandidate, studentCandidates, requestCandidates);
			boolean isDuplicate = dupResult.duplicateFlag();
			Map<String, Object> duplicateOf = null;
			if (isDuplicate) {
				duplicateCount++;
				duplicateOf = Map.of("students", dupResult.studentIds(), "requests", dupResult.requestIds());
			}

			AdmissionRequest req = new AdmissionRequest(
					row.submittedAt(),
					row.formData(),
					batch.getId(),
					importKey,
					isDuplicate,
					duplicateOf
			);
			req = requestRepo.save(req);
			addedCount++;

			// Add saved request to candidates for subsequent rows of the same file
			requestCandidates.add(new PersonCandidate(
					req.getId(), row.surname(), row.firstName(), row.middleName(), row.dob(), row.mobile1()));
		}

		// Update batch with final counts
		batch.setCounts(addedCount, skippedCount);
		batchRepo.save(batch);

		// Students audit row for the upload
		audit.record("Students", "Create", "ImportBatch#" + batch.getId(),
				Map.of("batchId", batch.getId(), "fileName", batch.getFileName(),
						"total", totalRows, "added", addedCount, "skipped", skippedCount, "invalid", invalidCount),
				null);

		return new ImportResultDto(
				batch.getId(),
				totalRows,
				addedCount,
				skippedCount,
				duplicateCount,
				readResult.invalidRows()
		);
	}



	static PersonCandidate toCandidate(AdmissionRequest req) {
		Map<String, Object> fd = req.getFormData();
		if (fd == null) {
			return new PersonCandidate(req.getId(), null, null, null, null, null);
		}
		String surname = fd.get("surname") != null ? fd.get("surname").toString() : null;
		String firstName = fd.get("firstName") != null ? fd.get("firstName").toString() : null;
		String middleName = fd.get("middleName") != null ? fd.get("middleName").toString() : null;
		LocalDate dob = null;
		if (fd.get("dob") != null) {
			try {
				dob = LocalDate.parse(fd.get("dob").toString());
			} catch (Exception ignored) {
			}
		}
		String mobile1 = fd.get("mobile1") != null ? fd.get("mobile1").toString() : null;
		return new PersonCandidate(req.getId(), surname, firstName, middleName, dob, mobile1);
	}

}

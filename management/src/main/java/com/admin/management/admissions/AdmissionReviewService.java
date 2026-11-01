package com.admin.management.admissions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.admissions.AdmissionDetailDto.DuplicateMatchDto;
import com.admin.management.audit.AuditService;
import com.admin.management.shared.CurrentUserProvider;
import com.admin.management.shared.masking.SensitiveMasker;
import com.admin.management.shared.table.TablePage;
import com.admin.management.shared.table.TableRequest;
import com.admin.management.shared.web.ApiProblem;
import com.admin.management.shared.web.CodedConflictException;
import com.admin.management.shared.web.NotFoundException;
import com.admin.management.shared.web.StaleVersionException;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.students.api.StudentLookup;
import com.admin.management.students.api.StudentRegistration;
import com.admin.management.students.api.StudentRegistration.RegistrationInput;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Service for reviewing, approving and rejecting admission requests (FR-033, FR-034, FR-035, FR-036).
 */
@Service
public class AdmissionReviewService {

	private final AdmissionRequestRepository requestRepo;
	private final StudentRegistration studentRegistration;
	private final StudentLookup studentLookup;
	private final AuditService audit;
	private final CurrentUserProvider currentUser;
	private final JdbcTemplate jdbc;
	private final EntityManager entityManager;

	public AdmissionReviewService(AdmissionRequestRepository requestRepo,
			StudentRegistration studentRegistration,
			StudentLookup studentLookup,
			AuditService audit,
			CurrentUserProvider currentUser,
			JdbcTemplate jdbc,
			EntityManager entityManager) {
		this.requestRepo = requestRepo;
		this.studentRegistration = studentRegistration;
		this.studentLookup = studentLookup;
		this.audit = audit;
		this.currentUser = currentUser;
		this.jdbc = jdbc;
		this.entityManager = entityManager;
	}

	public long getPendingCount() {
		return requestRepo.countByStatus(AdmissionRequest.STATUS_PENDING);
	}

	public AdmissionDetailDto getDetail(Long id) {
		AdmissionRequest request = requestRepo.findById(id)
				.orElseThrow(() -> new NotFoundException("AdmissionRequest", id));

		Map<String, Object> maskedForm = SensitiveMasker.sanitize(request.getFormData(), SensitiveMasker.MASKED);
		List<DuplicateMatchDto> matches = findDuplicateMatches(id, request.getDuplicateOf());

		return new AdmissionDetailDto(
				request.getId(),
				request.getStatus(),
				request.isDuplicateFlag(),
				request.getDuplicateOf(),
				matches,
				maskedForm,
				request.getVersion(),
				request.getSubmittedAt(),
				request.getRemarks(),
				request.getStudentId(),
				request.getReviewedBy(),
				request.getReviewedAt()
		);
	}

	@SuppressWarnings("unchecked")
	public TablePage list(String status, Boolean duplicate, int first, int rows, String search) {
		StringBuilder whereClause = new StringBuilder(" where 1=1 ");
		List<Object> params = new ArrayList<>();

		if (status != null && !status.isBlank() && !"All".equalsIgnoreCase(status.trim())) {
			whereClause.append(" and status = ? ");
			params.add(status.trim());
		}

		if (duplicate != null) {
			whereClause.append(" and duplicate_flag = ? ");
			params.add(duplicate);
		}

		if (search != null && !search.isBlank()) {
			String pattern = "%" + search.trim().toLowerCase() + "%";
			whereClause.append(" and (lower(form_data->>'surname') like ? "
					+ "or lower(form_data->>'firstName') like ? "
					+ "or lower(form_data->>'mobile1') like ? "
					+ "or lower(status) like ?) ");
			params.add(pattern);
			params.add(pattern);
			params.add(pattern);
			params.add(pattern);
		}

		String countSql = "select count(*) from admission_requests " + whereClause;
		Query countQuery = entityManager.createNativeQuery(countSql);
		for (int i = 0; i < params.size(); i++) {
			countQuery.setParameter(i + 1, params.get(i));
		}
		long total = ((Number) countQuery.getSingleResult()).longValue();
		if (total == 0) {
			return new TablePage(Collections.emptyList(), 0);
		}

		String selectSql = "select * from admission_requests " + whereClause
				+ " order by (status = 'Pending') desc, submitted_at desc nulls last, id desc limit ? offset ?";

		Query selectQuery = entityManager.createNativeQuery(selectSql, AdmissionRequest.class);
		for (int i = 0; i < params.size(); i++) {
			selectQuery.setParameter(i + 1, params.get(i));
		}
		int pageSize = rows > 0 ? rows : 25;
		int offset = Math.max(0, first);
		selectQuery.setParameter(params.size() + 1, pageSize);
		selectQuery.setParameter(params.size() + 2, offset);

		List<AdmissionRequest> requests = selectQuery.getResultList();
		List<Map<String, Object>> resultRows = new ArrayList<>(requests.size());

		for (AdmissionRequest req : requests) {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("id", req.getId());
			map.put("submittedAt", req.getSubmittedAt() != null ? req.getSubmittedAt().toString() : null);
			map.put("status", req.getStatus());
			map.put("duplicateFlag", req.isDuplicateFlag());

			Map<String, Object> formData = req.getFormData() != null ? req.getFormData() : Collections.emptyMap();
			String surname = getString(formData, "surname");
			String firstName = getString(formData, "firstName");
			String middleName = getString(formData, "middleName");
			String studentName = (surname != null ? surname : "") + ", " + (firstName != null ? firstName : "")
					+ (middleName != null && !middleName.isBlank() ? " " + middleName : "");
			map.put("studentName", studentName);
			map.put("surname", surname);
			map.put("firstName", firstName);
			map.put("middleName", middleName);
			map.put("gender", getString(formData, "gender"));
			map.put("dob", getString(formData, "dob"));
			map.put("mobile1", getString(formData, "mobile1"));
			map.put("admissionStandard", getString(formData, "admissionStandard"));
			map.put("admissionStandardId", formData.get("admissionStandardId"));

			Map<String, Object> maskedForm = SensitiveMasker.sanitize(formData, SensitiveMasker.MASKED);
			map.put("form", maskedForm);

			map.put("remarks", req.getRemarks());
			map.put("studentId", req.getStudentId());
			map.put("reviewedBy", req.getReviewedBy());
			map.put("reviewedAt", req.getReviewedAt() != null ? req.getReviewedAt().toString() : null);
			map.put("version", req.getVersion());

			resultRows.add(map);
		}

		return new TablePage(resultRows, total);
	}

	public TablePage search(TableRequest request) {
		String status = null;
		Boolean duplicate = null;
		if (request.filters() != null) {
			if (request.filters().containsKey("status")) {
				Object val = request.filters().get("status").value();
				if (val != null) {
					status = val.toString();
				}
			}
			if (request.filters().containsKey("duplicateFlag")) {
				Object val = request.filters().get("duplicateFlag").value();
				if (val instanceof Boolean b) {
					duplicate = b;
				} else if (val != null) {
					duplicate = Boolean.parseBoolean(val.toString());
				}
			}
		}
		return list(status, duplicate, request.first(), request.rows(), request.globalFilter());
	}

	@Transactional
	public Map<String, Object> approve(Long id, ApproveInput in) {
		AdmissionRequest request = requestRepo.findById(id)
				.orElseThrow(() -> new NotFoundException("AdmissionRequest", id));

		if (!AdmissionRequest.STATUS_PENDING.equals(request.getStatus())) {
			throw new CodedConflictException("ALREADY_REVIEWED", null, "This admission request has already been reviewed.", null);
		}

		if (in.version() != null && !Objects.equals(request.getVersion(), in.version())) {
			throw new StaleVersionException(getDetail(id));
		}

		List<ApiProblem.FieldError> errors = new ArrayList<>();
		if (in.grNo() == null || in.grNo().trim().isEmpty()) {
			errors.add(new ApiProblem.FieldError("grNo", "Enter the GR No."));
		}
		if (in.academicYearId() == null) {
			errors.add(new ApiProblem.FieldError("academicYearId", "Choose the academic year."));
		}
		if (in.divisionId() == null) {
			errors.add(new ApiProblem.FieldError("divisionId", "Choose the division."));
		}
		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}

		Map<String, Object> currentFormData = new LinkedHashMap<>(request.getFormData());
		if (in.form() != null) {
			for (Map.Entry<String, Object> entry : in.form().entrySet()) {
				String key = entry.getKey();
				Object val = entry.getValue();
				if (val == null) {
					continue;
				}
				String strVal = val.toString().trim();
				if ("aadhaar".equals(key) && strVal.contains("*")) {
					continue;
				}
				if ("religionCaste".equals(key) && strVal.contains("*")) {
					continue;
				}
				currentFormData.put(key, val);
			}
		}

		Long categoryId = in.categoryId();
		if (categoryId == null && currentFormData.get("categoryId") != null) {
			Object cObj = currentFormData.get("categoryId");
			if (cObj instanceof Number num) {
				categoryId = num.longValue();
			} else {
				try {
					categoryId = Long.parseLong(cObj.toString());
				} catch (Exception ignored) {
				}
			}
		}
		if (categoryId == null) {
			throw new ValidationException("categoryId", "Choose the category.");
		}

		Object stdObj = currentFormData.get("admissionStandardId");
		if (stdObj == null) {
			throw new ValidationException("admissionStandardId", "Missing admission standard.");
		}
		Long standardId = stdObj instanceof Number num ? num.longValue() : Long.parseLong(stdObj.toString());

		LocalDate dob = null;
		Object dobObj = currentFormData.get("dob");
		if (dobObj != null) {
			try {
				dob = LocalDate.parse(dobObj.toString());
			} catch (Exception ignored) {
			}
		}
		if (dob == null) {
			throw new ValidationException("dob", "Enter a valid date of birth.");
		}

		LocalDate admDate = in.admissionDate() != null ? in.admissionDate() : LocalDate.now();

		RegistrationInput regInput = new RegistrationInput(
				in.grNo().trim(),
				getString(currentFormData, "surname"),
				getString(currentFormData, "firstName"),
				getString(currentFormData, "middleName"),
				getString(currentFormData, "gender"),
				dob,
				getString(currentFormData, "birthPlace"),
				getString(currentFormData, "fatherName"),
				getString(currentFormData, "motherName"),
				getString(currentFormData, "mobile1"),
				getString(currentFormData, "mobile2"),
				getString(currentFormData, "address"),
				getString(currentFormData, "aadhaar"),
				getString(currentFormData, "religionCaste"),
				categoryId,
				admDate,
				standardId,
				getString(currentFormData, "previousSchool"),
				standardId,
				in.divisionId()
		);

		long studentId = studentRegistration.registerFromAdmission(regInput, in.academicYearId(), "Google Form");

		Long reviewerId = currentUser.currentUserId().orElse(1L);
		request.setFormData(currentFormData);
		request.approve(studentId, reviewerId);
		requestRepo.save(request);

		audit.record("Students", "Create", "AdmissionRequest#" + id,
				Map.of("requestId", id, "studentId", studentId, "status", "Approved", "grNo", in.grNo().trim()), null);

		return Map.of("id", id, "studentId", studentId, "status", "Approved");
	}

	@Transactional
	public Map<String, Object> reject(Long id, RejectInput in) {
		if (in == null || in.remarks() == null || in.remarks().trim().isEmpty()) {
			throw new ValidationException("remarks", "Enter the remarks.");
		}

		AdmissionRequest request = requestRepo.findById(id)
				.orElseThrow(() -> new NotFoundException("AdmissionRequest", id));

		if (!AdmissionRequest.STATUS_PENDING.equals(request.getStatus())) {
			throw new CodedConflictException("ALREADY_REVIEWED", null, "This admission request has already been reviewed.", null);
		}

		if (in.version() != null && !Objects.equals(request.getVersion(), in.version())) {
			throw new StaleVersionException(getDetail(id));
		}

		Long reviewerId = currentUser.currentUserId().orElse(1L);
		String remarks = in.remarks().trim();
		request.reject(remarks, reviewerId);
		requestRepo.save(request);

		audit.record("Students", "Edit", "AdmissionRequest#" + id,
				Map.of("requestId", id, "status", "Rejected", "remarks", remarks), null);

		return Map.of("id", id, "status", "Rejected");
	}

	private List<DuplicateMatchDto> findDuplicateMatches(Long currentRequestId, Map<String, Object> duplicateOf) {
		if (duplicateOf == null || duplicateOf.isEmpty()) {
			return Collections.emptyList();
		}

		List<DuplicateMatchDto> matches = new ArrayList<>();
		Object studentsObj = duplicateOf.get("students");
		if (studentsObj instanceof List<?> sIds) {
			for (Object item : sIds) {
				if (item instanceof Number num) {
					long sId = num.longValue();
					studentLookup.summary(sId).ifPresent(sum -> {
						matches.add(new DuplicateMatchDto("Student", sId,
								sum.grNo() + " - " + sum.fullName() + " (" + sum.status() + ")", "/students/" + sId));
					});
				}
			}
		}

		Object requestsObj = duplicateOf.get("requests");
		if (requestsObj instanceof List<?> rIds) {
			for (Object item : rIds) {
				if (item instanceof Number num) {
					long rId = num.longValue();
					if (rId != currentRequestId) {
						requestRepo.findById(rId).ifPresent(otherReq -> {
							Map<String, Object> fd = otherReq.getFormData();
							String name = getString(fd, "surname") + ", " + getString(fd, "firstName");
							String st = otherReq.getStatus();
							matches.add(new DuplicateMatchDto("Request", rId,
									"Request #" + rId + " - " + name + " (" + st + ")", "/admissions/" + rId));
						});
					}
				}
			}
		}

		return matches;
	}

	private static String getString(Map<String, Object> map, String key) {
		if (map == null) {
			return null;
		}
		Object val = map.get(key);
		return val != null ? val.toString() : null;
	}

}

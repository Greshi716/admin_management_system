package com.admin.management.students;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.shared.ClassRecordFees;
import com.admin.management.shared.web.ApiProblem.FieldError;
import com.admin.management.shared.web.NotFoundException;
import com.admin.management.shared.web.StaleVersionException;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.students.api.ClassRecordPromotion;

/**
 * Implements class record promotion writes and candidate reads in the students module
 * (FR-008, FR-010, FR-011, FR-016, FR-018, FR-019).
 */
@Service
public class ClassRecordPromotionService implements ClassRecordPromotion {

	private final JdbcTemplate jdbc;

	private final EnrollmentRepository enrollments;

	private final StudentRepository students;

	private final ClassRecordFees classRecordFees;

	private final MasterDataLookup masterData;

	private final StudentAudit audit;

	public ClassRecordPromotionService(JdbcTemplate jdbc, EnrollmentRepository enrollments,
			StudentRepository students, ClassRecordFees classRecordFees,
			MasterDataLookup masterData, StudentAudit audit) {
		this.jdbc = jdbc;
		this.enrollments = enrollments;
		this.students = students;
		this.classRecordFees = classRecordFees;
		this.masterData = masterData;
		this.audit = audit;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockStandardStudying(long academicYearId, long standardId) {
		jdbc.queryForList("""
				select id from student_enrollments
				where academic_year_id = ? and standard_id = ? and year_status = 'Studying'
				for update
				""", academicYearId, standardId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<CandidateStudent> studentsToPromote(long fromYearId, long toYearId, long standardId) {
		return jdbc.query("""
				select e.id as enrollment_id,
				       s.id as student_id,
				       s.gr_no,
				       trim(s.surname || ' ' || s.first_name || coalesce(' ' || nullif(s.middle_name, ''), '')) as student_name,
				       d.id as division_id,
				       d.name as division_name,
				       c.id as category_id,
				       c.name as category_name,
				       e.stream,
				       e.version
				from student_enrollments e
				join students s on s.id = e.student_id
				join divisions d on d.id = e.division_id
				join student_categories c on c.id = e.category_id
				where e.academic_year_id = ?
				  and e.standard_id = ?
				  and e.year_status = 'Studying'
				  and s.status = 'Active'
				  and not exists (
				      select 1 from student_enrollments e2
				      where e2.student_id = s.id and e2.academic_year_id = ?
				  )
				order by s.surname, s.first_name, s.gr_no
				""", (rs, rowNum) -> new CandidateStudent(
						rs.getLong("enrollment_id"),
						rs.getLong("student_id"),
						rs.getString("gr_no"),
						rs.getString("student_name"),
						rs.getLong("division_id"),
						rs.getString("division_name"),
						rs.getLong("category_id"),
						rs.getString("category_name"),
						rs.getString("stream"),
						rs.getLong("version")),
				fromYearId, standardId, toYearId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<PendingStandard> pendingByStandard(long fromYearId, long toYearId) {
		return jdbc.query("""
				select st.id as standard_id,
				       st.name as standard_name,
				       count(e.id)::int as pending_count
				from standards st
				join student_enrollments e on e.standard_id = st.id and e.academic_year_id = ? and e.year_status = 'Studying'
				join students s on s.id = e.student_id and s.status = 'Active'
				where not exists (
				    select 1 from student_enrollments e2
				    where e2.student_id = s.id and e2.academic_year_id = ?
				)
				group by st.id, st.name, st.display_order
				having count(e.id) > 0
				order by st.display_order
				""", (rs, rowNum) -> new PendingStandard(
						rs.getLong("standard_id"),
						rs.getString("standard_name"),
						rs.getInt("pending_count")),
				fromYearId, toYearId);
	}

	@Override
	@Transactional
	public Long recordOutcome(PromotionItemOutcome outcome) {
		StudentEnrollment oldRecord = enrollments.findById(outcome.fromEnrollmentId())
				.orElseThrow(() -> new NotFoundException("Class record", outcome.fromEnrollmentId()));

		if (!Objects.equals(oldRecord.getVersion(), outcome.version())) {
			throw new StaleVersionException(oldRecord.getVersion());
		}

		if (!StudentEnrollment.STUDYING.equalsIgnoreCase(oldRecord.getYearStatus())) {
			throw new ValidationException("ALREADY_PROCESSED",
					List.of(new FieldError("fromEnrollmentId", "Student is no longer studying in this academic year.")));
		}

		Student student = students.findById(oldRecord.getStudentId())
				.orElseThrow(() -> new NotFoundException("Student", oldRecord.getStudentId()));

		Optional<StudentEnrollment> existingTarget = enrollments.findByStudentIdAndAcademicYearId(
				student.getId(), outcome.toYearId());
		if (existingTarget.isPresent()) {
			throw new ValidationException("ALREADY_PROCESSED",
					List.of(new FieldError("studentId", "Student already has a class record in academic year " + outcome.toYearId())));
		}

		String normalizedOutcome = normalizeOutcome(outcome.outcome());

		Map<String, Object> oldValues = new LinkedHashMap<>();
		oldValues.put("standardId", oldRecord.getStandardId());
		oldValues.put("divisionId", oldRecord.getDivisionId());
		oldValues.put("categoryId", oldRecord.getCategoryId());
		oldValues.put("stream", oldRecord.getStream());
		oldValues.put("yearStatus", oldRecord.getYearStatus());

		Map<String, Object> newValues = new LinkedHashMap<>();

		if ("Left".equalsIgnoreCase(normalizedOutcome)) {
			if (Student.LEFT.equals(student.getStatus())) {
				throw new ValidationException("ALREADY_PROCESSED",
						List.of(new FieldError("studentId", "This student is already marked as left.")));
			}
			Map<String, Object> studentBefore = student.snapshot();
			student.markLeft();
			Student savedStudent = students.saveAndFlush(student);
			audit.edited(StudentAudit.STUDENT, savedStudent.getId(), studentBefore, savedStudent.snapshot());

			oldRecord.setYearStatus("Left");
			enrollments.saveAndFlush(oldRecord);

			newValues.put("standardId", outcome.toStandardId());
			newValues.put("divisionId", outcome.toDivisionId());
			newValues.put("categoryId", outcome.toCategoryId() != null ? outcome.toCategoryId() : oldRecord.getCategoryId());
			newValues.put("stream", outcome.stream());
			newValues.put("yearStatus", "Left");

			audit.promotion(oldRecord.getId(), oldValues, newValues);
			return null;
		}

		LocalDate yearStartDate = masterData.yearStartDate(outcome.toYearId())
				.orElseThrow(() -> new NotFoundException("Academic year", outcome.toYearId()));

		Long toCategoryId = outcome.toCategoryId() != null ? outcome.toCategoryId() : oldRecord.getCategoryId();

		oldRecord.setYearStatus(normalizedOutcome);
		enrollments.saveAndFlush(oldRecord);

		StudentEnrollment newRecord = new StudentEnrollment(student.getId(), outcome.toYearId(),
				outcome.toStandardId(), outcome.toDivisionId(), toCategoryId, yearStartDate);
		newRecord.setStream(outcome.stream());
		newRecord.setYearStatus(StudentEnrollment.STUDYING);
		StudentEnrollment savedNew = enrollments.saveAndFlush(newRecord);

		classRecordFees.classRecordAdded(savedNew.getId(), outcome.toYearId(), savedNew.getStandardId(),
				savedNew.getCategoryId());

		newValues.put("standardId", outcome.toStandardId());
		newValues.put("divisionId", outcome.toDivisionId());
		newValues.put("categoryId", toCategoryId);
		newValues.put("stream", outcome.stream());
		newValues.put("yearStatus", normalizedOutcome);

		audit.promotion(oldRecord.getId(), oldValues, newValues);

		return savedNew.getId();
	}

	private static String normalizeOutcome(String outcome) {
		if (outcome == null) {
			throw new ValidationException("outcome", "Promotion outcome is required.");
		}
		if ("PROMOTED".equalsIgnoreCase(outcome) || "Promoted".equalsIgnoreCase(outcome)) {
			return "Promoted";
		}
		if ("DETAINED".equalsIgnoreCase(outcome) || "Detained".equalsIgnoreCase(outcome)) {
			return "Detained";
		}
		if ("LEFT".equalsIgnoreCase(outcome) || "Left".equalsIgnoreCase(outcome)) {
			return "Left";
		}
		throw new ValidationException("outcome", "Unknown promotion outcome: " + outcome);
	}

}

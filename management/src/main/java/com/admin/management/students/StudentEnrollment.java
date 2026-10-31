package com.admin.management.students;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** The yearly class record: one row per student per academic year (FR-006). Never deleted. */
@Entity
@Table(name = "student_enrollments")
public class StudentEnrollment {

	public static final String STUDYING = "Studying";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "student_id", nullable = false, updatable = false)
	private Long studentId;

	@Column(name = "academic_year_id", nullable = false, updatable = false)
	private Long academicYearId;

	@Column(name = "standard_id", nullable = false)
	private Long standardId;

	@Column(name = "division_id", nullable = false)
	private Long divisionId;

	@Column(name = "category_id", nullable = false)
	private Long categoryId;

	/** Copied from the academic year when the row is created; only used to find a student's most recent record. */
	@Column(name = "year_start_date", nullable = false, updatable = false)
	private LocalDate yearStartDate;

	@Column(name = "year_status", nullable = false)
	private String yearStatus = STUDYING;

	@Column(name = "stream")
	private String stream;

	@Version
	private Long version;

	protected StudentEnrollment() {
	}

	public StudentEnrollment(Long studentId, Long academicYearId, Long standardId, Long divisionId, Long categoryId,
			LocalDate yearStartDate) {
		this.studentId = studentId;
		this.academicYearId = academicYearId;
		this.standardId = standardId;
		this.divisionId = divisionId;
		this.categoryId = categoryId;
		this.yearStartDate = yearStartDate;
	}

	public void change(Long newStandardId, Long newDivisionId, Long newCategoryId) {
		change(newStandardId, newDivisionId, newCategoryId, this.stream);
	}

	public void change(Long newStandardId, Long newDivisionId, Long newCategoryId, String stream) {
		this.standardId = newStandardId;
		this.divisionId = newDivisionId;
		this.categoryId = newCategoryId;
		this.stream = stream;
	}

	public Long getId() {
		return id;
	}

	public Long getStudentId() {
		return studentId;
	}

	public Long getAcademicYearId() {
		return academicYearId;
	}

	public Long getStandardId() {
		return standardId;
	}

	public Long getDivisionId() {
		return divisionId;
	}

	public Long getCategoryId() {
		return categoryId;
	}

	public LocalDate getYearStartDate() {
		return yearStartDate;
	}

	public String getYearStatus() {
		return yearStatus;
	}

	public String getStream() {
		return stream;
	}

	public void setStream(String stream) {
		this.stream = stream;
	}

	public void setYearStatus(String yearStatus) {
		this.yearStatus = yearStatus;
	}

	public Long getVersion() {
		return version;
	}

	public Map<String, Object> snapshot() {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("studentId", studentId);
		values.put("academicYearId", academicYearId);
		values.put("standardId", standardId);
		values.put("divisionId", divisionId);
		values.put("categoryId", categoryId);
		values.put("yearStatus", yearStatus);
		values.put("stream", stream);
		return values;
	}

}

package com.admin.management.students;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A GR No. that was given to a student. Never removed, so a corrected number stays reserved (FR-002). */
@Entity
@Table(name = "student_gr_numbers")
public class StudentGrNumber {

	@Id
	@Column(name = "gr_key")
	private String grKey;

	@Column(name = "student_id", nullable = false)
	private Long studentId;

	@Column(name = "assigned_at", nullable = false)
	private Instant assignedAt;

	protected StudentGrNumber() {
	}

	public StudentGrNumber(String grNo, Long studentId, Instant assignedAt) {
		this.grKey = keyOf(grNo);
		this.studentId = studentId;
		this.assignedAt = assignedAt;
	}

	/** The comparison key: trimmed and lower-case. */
	public static String keyOf(String grNo) {
		return grNo.trim().toLowerCase(Locale.ROOT);
	}

	public String getGrKey() {
		return grKey;
	}

	public Long getStudentId() {
		return studentId;
	}

	public Instant getAssignedAt() {
		return assignedAt;
	}

}

package com.admin.management.admissions;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * An admission request from an uploaded Google Form responses row (FR-030, FR-031, FR-035).
 */
@Entity
@Table(name = "admission_requests")
public class AdmissionRequest {

	public static final String STATUS_PENDING = "Pending";
	public static final String STATUS_APPROVED = "Approved";
	public static final String STATUS_REJECTED = "Rejected";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "form_data", columnDefinition = "jsonb", nullable = false)
	private Map<String, Object> formData;

	@Column(name = "import_batch_id", nullable = false)
	private Long importBatchId;

	@Column(name = "import_key", nullable = false, unique = true)
	private String importKey;

	@Column(name = "duplicate_flag", nullable = false)
	private boolean duplicateFlag;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "duplicate_of", columnDefinition = "jsonb")
	private Map<String, Object> duplicateOf;

	@Column(nullable = false)
	private String status = STATUS_PENDING;

	@Column(name = "reviewed_by")
	private Long reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	private String remarks;

	@Column(name = "student_id")
	private Long studentId;

	@Version
	private Long version;

	protected AdmissionRequest() {
	}

	public AdmissionRequest(Instant submittedAt, Map<String, Object> formData, Long importBatchId,
			String importKey, boolean duplicateFlag, Map<String, Object> duplicateOf) {
		this.submittedAt = submittedAt;
		this.formData = formData;
		this.importBatchId = importBatchId;
		this.importKey = importKey;
		this.duplicateFlag = duplicateFlag;
		this.duplicateOf = duplicateOf;
		this.status = STATUS_PENDING;
	}

	public void approve(Long studentId, Long reviewerId) {
		this.status = STATUS_APPROVED;
		this.studentId = studentId;
		this.reviewedBy = reviewerId;
		this.reviewedAt = Instant.now();
	}

	public void reject(String remarks, Long reviewerId) {
		this.status = STATUS_REJECTED;
		this.remarks = remarks;
		this.reviewedBy = reviewerId;
		this.reviewedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public Map<String, Object> getFormData() {
		return formData;
	}

	public void setFormData(Map<String, Object> formData) {
		this.formData = formData;
	}

	public Long getImportBatchId() {
		return importBatchId;
	}

	public String getImportKey() {
		return importKey;
	}

	public boolean isDuplicateFlag() {
		return duplicateFlag;
	}

	public Map<String, Object> getDuplicateOf() {
		return duplicateOf;
	}

	public String getStatus() {
		return status;
	}

	public Long getReviewedBy() {
		return reviewedBy;
	}

	public Instant getReviewedAt() {
		return reviewedAt;
	}

	public String getRemarks() {
		return remarks;
	}

	public Long getStudentId() {
		return studentId;
	}

	public Long getVersion() {
		return version;
	}

}

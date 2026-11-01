package com.admin.management.admissions;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Record of a responses file upload batch (FR-028, FR-030).
 */
@Entity
@Table(name = "admission_import_batches")
public class ImportBatch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Column(name = "uploaded_by", nullable = false)
	private Long uploadedBy;

	@Column(name = "uploaded_at", nullable = false)
	private Instant uploadedAt = Instant.now();

	@Column(name = "rows_total", nullable = false)
	private int rowsTotal;

	@Column(name = "rows_added", nullable = false)
	private int rowsAdded;

	@Column(name = "rows_skipped", nullable = false)
	private int rowsSkipped;

	@Column(name = "rows_invalid", nullable = false)
	private int rowsInvalid;

	protected ImportBatch() {
	}

	public ImportBatch(String fileName, Long uploadedBy, int rowsTotal, int rowsAdded, int rowsSkipped, int rowsInvalid) {
		this.fileName = fileName;
		this.uploadedBy = uploadedBy;
		this.rowsTotal = rowsTotal;
		this.rowsAdded = rowsAdded;
		this.rowsSkipped = rowsSkipped;
		this.rowsInvalid = rowsInvalid;
	}

	public Long getId() {
		return id;
	}

	public String getFileName() {
		return fileName;
	}

	public Long getUploadedBy() {
		return uploadedBy;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

	public int getRowsTotal() {
		return rowsTotal;
	}

	public int getRowsAdded() {
		return rowsAdded;
	}

	public int getRowsSkipped() {
		return rowsSkipped;
	}

	public int getRowsInvalid() {
		return rowsInvalid;
	}

	public void setCounts(int rowsAdded, int rowsSkipped) {
		this.rowsAdded = rowsAdded;
		this.rowsSkipped = rowsSkipped;
	}

}

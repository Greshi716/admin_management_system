package com.admin.management.fees;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Whether a year's fee structure is still a Draft or is Finalised (FR-002). A year with no row is a Draft with no
 * fee rows. The row is also what finalise, revise and a new class record lock, so they cannot overlap. It goes from
 * Draft to Finalised once and never back.
 */
@Entity
@Table(name = "fee_year_status")
public class FeeYearStatus {

	public static final String DRAFT = "Draft";

	public static final String FINALISED = "Finalised";

	@Id
	@Column(name = "academic_year_id")
	private Long academicYearId;

	@Column(nullable = false)
	private String status = DRAFT;

	@Column(name = "finalised_at")
	private Instant finalisedAt;

	@Version
	private Long version;

	protected FeeYearStatus() {
	}

	public FeeYearStatus(Long academicYearId) {
		this.academicYearId = academicYearId;
	}

	/** Marks the structure Finalised. Calling it again keeps the first time. */
	public void finalise(Instant now) {
		if (!isFinalised()) {
			this.status = FINALISED;
			this.finalisedAt = now;
		}
	}

	public boolean isFinalised() {
		return FINALISED.equals(status);
	}

	public Long getAcademicYearId() {
		return academicYearId;
	}

	public String getStatus() {
		return status;
	}

	public Instant getFinalisedAt() {
		return finalisedAt;
	}

	public Long getVersion() {
		return version;
	}

}

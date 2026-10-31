package com.admin.management.yearchange;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only record of one student's outcome in a promotion run (FR-016, FR-017, FR-025).
 */
@Entity
@Table(name = "promotion_run_items")
public class PromotionRunItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "run_id", nullable = false, updatable = false)
	private Long runId;

	@Column(name = "student_id", nullable = false, updatable = false)
	private Long studentId;

	@Column(name = "from_enrollment_id", nullable = false, unique = true, updatable = false)
	private Long fromEnrollmentId;

	@Column(name = "to_enrollment_id", updatable = false)
	private Long toEnrollmentId;

	@Column(name = "outcome", nullable = false, updatable = false)
	private String outcome;

	@Column(name = "to_standard_id", updatable = false)
	private Long toStandardId;

	@Column(name = "to_division_id", updatable = false)
	private Long toDivisionId;

	@Column(name = "to_category_id", updatable = false)
	private Long toCategoryId;

	@Column(name = "carried_amount", nullable = false, updatable = false)
	private BigDecimal carriedAmount = BigDecimal.ZERO;

	@Column(name = "brought_forward_before", nullable = false, updatable = false)
	private BigDecimal broughtForwardBefore = BigDecimal.ZERO;

	@Column(name = "brought_forward_after", nullable = false, updatable = false)
	private BigDecimal broughtForwardAfter = BigDecimal.ZERO;

	protected PromotionRunItem() {
	}

	public PromotionRunItem(Long runId, Long studentId, Long fromEnrollmentId, Long toEnrollmentId,
			String outcome, Long toStandardId, Long toDivisionId, Long toCategoryId,
			BigDecimal carriedAmount, BigDecimal broughtForwardBefore, BigDecimal broughtForwardAfter) {
		this.runId = runId;
		this.studentId = studentId;
		this.fromEnrollmentId = fromEnrollmentId;
		this.toEnrollmentId = toEnrollmentId;
		this.outcome = outcome;
		this.toStandardId = toStandardId;
		this.toDivisionId = toDivisionId;
		this.toCategoryId = toCategoryId;
		this.carriedAmount = carriedAmount;
		this.broughtForwardBefore = broughtForwardBefore;
		this.broughtForwardAfter = broughtForwardAfter;
	}

	public Long getId() {
		return id;
	}

	public Long getRunId() {
		return runId;
	}

	public Long getStudentId() {
		return studentId;
	}

	public Long getFromEnrollmentId() {
		return fromEnrollmentId;
	}

	public Long getToEnrollmentId() {
		return toEnrollmentId;
	}

	public String getOutcome() {
		return outcome;
	}

	public Long getToStandardId() {
		return toStandardId;
	}

	public Long getToDivisionId() {
		return toDivisionId;
	}

	public Long getToCategoryId() {
		return toCategoryId;
	}

	public BigDecimal getCarriedAmount() {
		return carriedAmount;
	}

	public BigDecimal getBroughtForwardBefore() {
		return broughtForwardBefore;
	}

	public BigDecimal getBroughtForwardAfter() {
		return broughtForwardAfter;
	}

}

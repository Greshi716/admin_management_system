package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * The fee of one class record of a finalised year (FR-006). A null amount means "Fee not set"; a null registration
 * fee means none. Accounts are created and updated, never deleted. The id is the class record's id, assigned by the
 * caller, so the entity says whether it is new.
 */
@Entity
@Table(name = "fee_accounts")
public class FeeAccount implements Persistable<Long> {

	@Id
	@Column(name = "enrollment_id")
	private Long enrollmentId;

	@Column(name = "academic_year_id", nullable = false, updatable = false)
	private Long academicYearId;

	@Column(precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "registration_fee", precision = 12, scale = 2)
	private BigDecimal registrationFee;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Transient
	private boolean isNew = true;

	protected FeeAccount() {
	}

	public FeeAccount(Long enrollmentId, Long academicYearId, BigDecimal amount, BigDecimal registrationFee,
			LocalDate dueDate, Instant now) {
		this.enrollmentId = enrollmentId;
		this.academicYearId = academicYearId;
		change(amount, registrationFee, dueDate, now);
	}

	public void change(BigDecimal newAmount, BigDecimal newRegistrationFee, LocalDate newDueDate, Instant now) {
		this.amount = Money.scale2(newAmount);
		this.registrationFee = Money.scale2(newRegistrationFee);
		this.dueDate = newDueDate;
		this.updatedAt = now;
	}

	@Override
	public Long getId() {
		return enrollmentId;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}

	public Long getEnrollmentId() {
		return enrollmentId;
	}

	public Long getAcademicYearId() {
		return academicYearId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BigDecimal getRegistrationFee() {
		return registrationFee;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}

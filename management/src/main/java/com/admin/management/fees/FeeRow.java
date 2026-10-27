package com.admin.management.fees;

import java.math.BigDecimal;
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

/**
 * One row of a year's fee structure: the yearly fee, an optional registration fee and an optional due date for a
 * standard and a category (no category means all categories of the standard) (FR-001). The ids of other modules'
 * records are plain numbers.
 */
@Entity
@Table(name = "fee_rows")
public class FeeRow {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "academic_year_id", nullable = false, updatable = false)
	private Long academicYearId;

	@Column(name = "standard_id", nullable = false)
	private Long standardId;

	@Column(name = "category_id")
	private Long categoryId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "registration_fee", precision = 12, scale = 2)
	private BigDecimal registrationFee;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@Version
	private Long version;

	protected FeeRow() {
	}

	public FeeRow(Long academicYearId, Long standardId, Long categoryId, BigDecimal amount, BigDecimal registrationFee,
			LocalDate dueDate) {
		this.academicYearId = academicYearId;
		change(standardId, categoryId, amount, registrationFee, dueDate);
	}

	public void change(Long newStandardId, Long newCategoryId, BigDecimal newAmount, BigDecimal newRegistrationFee,
			LocalDate newDueDate) {
		this.standardId = newStandardId;
		this.categoryId = newCategoryId;
		this.amount = Money.scale2(newAmount);
		this.registrationFee = Money.scale2(newRegistrationFee);
		this.dueDate = newDueDate;
	}

	public Long getId() {
		return id;
	}

	public Long getAcademicYearId() {
		return academicYearId;
	}

	public Long getStandardId() {
		return standardId;
	}

	public Long getCategoryId() {
		return categoryId;
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

	public Long getVersion() {
		return version;
	}

	/** The values an audit entry records: amounts as plain text with two decimals, the date as text. */
	public Map<String, Object> snapshot() {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("standardId", standardId);
		values.put("categoryId", categoryId);
		values.put("amount", Money.text(amount));
		values.put("registrationFee", Money.text(registrationFee));
		values.put("dueDate", dueDate == null ? null : dueDate.toString());
		return values;
	}

}

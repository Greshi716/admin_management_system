package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One fee receipt (FR-009). A receipt is never updated or deleted: the entity is immutable and the database refuses
 * a change as well (V8 triggers). The name, GR number, class and division names are copied from the student at the
 * time, so a later rename does not change a printed receipt. The ids of other modules' records are plain numbers.
 */
@Entity
@Immutable
@Table(name = "receipts")
public class Receipt {

	public enum Type {

		CURRENT_YEAR, PREVIOUS_YEARS

	}

	public enum Mode {

		CASH, CHEQUE

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "serial_no", updatable = false)
	private Long serialNo;

	@Column(name = "receipt_no", nullable = false, updatable = false)
	private String receiptNo;

	@Column(name = "receipt_date", nullable = false, updatable = false)
	private LocalDate receiptDate;

	@Column(name = "academic_year_id", nullable = false, updatable = false)
	private Long academicYearId;

	@Column(name = "student_id", nullable = false, updatable = false)
	private Long studentId;

	@Column(name = "enrollment_id", updatable = false)
	private Long enrollmentId;

	@Column(name = "student_name", nullable = false, updatable = false)
	private String studentName;

	@Column(name = "gr_no", nullable = false, updatable = false)
	private String grNo;

	@Column(name = "standard_id", updatable = false)
	private Long standardId;

	@Column(name = "division_id", updatable = false)
	private Long divisionId;

	@Column(name = "category_id", updatable = false)
	private Long categoryId;

	@Column(name = "standard_name", updatable = false)
	private String standardName;

	@Column(name = "division_name", updatable = false)
	private String divisionName;

	@Enumerated(EnumType.STRING)
	@Column(name = "receipt_type", nullable = false, updatable = false)
	private Type receiptType;

	@Column(name = "registration_paid", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal registrationPaid;

	@Column(name = "fee_paid", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal feePaid;

	@Column(nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal total;

	@Column(name = "total_words", nullable = false, updatable = false)
	private String totalWords;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_mode", nullable = false, updatable = false)
	private Mode paymentMode;

	@Column(name = "cheque_no", updatable = false)
	private String chequeNo;

	@Column(name = "bank_name", updatable = false)
	private String bankName;

	@Column(name = "cheque_date", updatable = false)
	private LocalDate chequeDate;

	@Column(name = "created_by", nullable = false, updatable = false)
	private Long createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Receipt() {
	}

	/** The fields of a receipt to be saved; amounts are brought to two decimals and refused if they have more. */
	public record Draft(String receiptNo, LocalDate receiptDate, Long academicYearId, Long studentId,
			Long enrollmentId, String studentName, String grNo, Long standardId, Long divisionId, Long categoryId,
			String standardName, String divisionName, Type receiptType, BigDecimal registrationPaid,
			BigDecimal feePaid, String totalWords, Mode paymentMode, String chequeNo, String bankName,
			LocalDate chequeDate, Long createdBy, Instant createdAt) {
	}

	public Receipt(Draft d) {
		this.receiptNo = d.receiptNo();
		this.receiptDate = d.receiptDate();
		this.academicYearId = d.academicYearId();
		this.studentId = d.studentId();
		this.enrollmentId = d.enrollmentId();
		this.studentName = d.studentName();
		this.grNo = d.grNo();
		this.standardId = d.standardId();
		this.divisionId = d.divisionId();
		this.categoryId = d.categoryId();
		this.standardName = d.standardName();
		this.divisionName = d.divisionName();
		this.receiptType = d.receiptType();
		this.registrationPaid = Money.scale2(d.registrationPaid());
		this.feePaid = Money.scale2(d.feePaid());
		this.total = this.registrationPaid.add(this.feePaid);
		this.totalWords = d.totalWords();
		this.paymentMode = d.paymentMode();
		this.chequeNo = d.chequeNo();
		this.bankName = d.bankName();
		this.chequeDate = d.chequeDate();
		this.createdBy = d.createdBy();
		this.createdAt = d.createdAt();
	}

	public Long getSerialNo() {
		return serialNo;
	}

	public String getReceiptNo() {
		return receiptNo;
	}

	public LocalDate getReceiptDate() {
		return receiptDate;
	}

	public Long getAcademicYearId() {
		return academicYearId;
	}

	public Long getStudentId() {
		return studentId;
	}

	public Long getEnrollmentId() {
		return enrollmentId;
	}

	public String getStudentName() {
		return studentName;
	}

	public String getGrNo() {
		return grNo;
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

	public String getStandardName() {
		return standardName;
	}

	public String getDivisionName() {
		return divisionName;
	}

	public Type getReceiptType() {
		return receiptType;
	}

	public BigDecimal getRegistrationPaid() {
		return registrationPaid;
	}

	public BigDecimal getFeePaid() {
		return feePaid;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public String getTotalWords() {
		return totalWords;
	}

	public Mode getPaymentMode() {
		return paymentMode;
	}

	public String getChequeNo() {
		return chequeNo;
	}

	public String getBankName() {
		return bankName;
	}

	public LocalDate getChequeDate() {
		return chequeDate;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
